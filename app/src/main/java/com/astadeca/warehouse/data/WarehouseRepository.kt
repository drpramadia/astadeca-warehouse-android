package com.astadeca.warehouse.data

import org.json.JSONArray
import org.json.JSONObject

/** Satu tempat untuk semua query warehouse. UI tidak menulis query PostgREST. */
class WarehouseRepository(private val api: SupabaseClient) {

    // ---------- Sesi ----------

    /** Ambil info sesi (role, org) dari RPC yang sama dengan web. */
    fun mySession(): JSONObject {
        val t = api.rpc("get_my_session", JSONObject())
        return when (val v = t.nextValue()) {
            is JSONObject -> v
            is JSONArray -> if (v.length() > 0) v.getJSONObject(0) else JSONObject()
            else -> JSONObject()
        }
    }

    fun myPermissions(): List<String> {
        val t = api.rpc("get_my_permissions", JSONObject())
        val out = mutableListOf<String>()
        when (val v = t.nextValue()) {
            is JSONArray -> for (i in 0 until v.length()) {
                val row = v.optJSONObject(i) ?: continue
                row.optString("permission_code").takeIf { it.isNotBlank() }?.let { out += it }
            }
            is JSONObject -> v.optString("permission_code").takeIf { it.isNotBlank() }?.let { out += it }
        }
        return out
    }

    // ---------- Inventory ----------

    fun inventory(search: String = "", limit: Int = 100): JSONArray {
        val q = buildString {
            append("select=id,product_id,batch_number,quantity_kg,status,expiry_date,received_at,basket_id,")
            append("products(name,sku),cold_storage_baskets(code,cold_storage_zones(name,cold_storages(name)))")
            if (search.isNotBlank()) {
                val safe = Query.safeSearch(search)
                append("&or=(batch_number.ilike.*$safe*,products.name.ilike.*$safe*,products.sku.ilike.*$safe*)")
            }
            append("&order=received_at.desc.nullslast&limit=$limit")
        }
        return api.get("inventory", q)
    }

    fun inventoryByBasket(basketCode: String): JSONArray =
        api.get(
            "inventory",
            "select=id,product_id,batch_number,quantity_kg,status,expiry_date,products(name,sku),cold_storage_baskets(code)" +
                "&cold_storage_baskets.code=eq.${enc(basketCode)}&order=received_at.asc.nullslast",
        )

    fun inventoryByBatch(batch: String): JSONArray =
        api.get(
            "inventory",
            "select=id,product_id,batch_number,quantity_kg,status,expiry_date,products(name,sku),cold_storage_baskets(code)" +
                "&batch_number=eq.${enc(batch)}&order=received_at.asc.nullslast",
        )

    // ---------- Products ----------

    fun products(limit: Int = 500): JSONArray =
        api.get("products", "select=id,name,sku,unit_id&is_active=eq.true&order=name.asc&limit=$limit")

    // ---------- Baskets ----------

    fun baskets(search: String = "", limit: Int = 200): JSONArray {
        val q = buildString {
            append("select=id,code,capacity_kg,status,zone_id,cold_storage_zones(name,cold_storages(name))")
            if (search.isNotBlank()) append("&code=ilike.*${enc(search)}*")
            append("&order=code.asc&limit=$limit")
        }
        return api.get("cold_storage_baskets", q)
    }

    fun createBasket(organizationId: String, zoneId: String, code: String, capacityKg: Double): Result<String> =
        runCatching {
            val body = JSONObject()
                .put("organization_id", organizationId)
                .put("zone_id", zoneId)
                .put("code", code)
                .put("capacity_kg", capacityKg)
                .put("status", "AVAILABLE")
            api.insert("cold_storage_baskets", body)
        }

    fun zones(): JSONArray =
        api.get("cold_storage_zones", "select=id,name,code,cold_storage_id,cold_storages(name)&order=name.asc")

    // ---------- Goods Receipt ----------

    fun goodsReceipts(limit: Int = 100): JSONArray =
        api.get(
            "goods_receipts",
            "select=id,gr_number,received_at,notes,purchase_orders(po_number),goods_receipt_lines(id,product_id,batch_number,quantity_kg,quantity_received,condition,products(name,sku),delivery_order_id)" +
                "&order=received_at.desc&limit=$limit",
        )

    fun goodsReceiptDetail(grId: String): JSONObject? =
        api.get(
            "goods_receipts",
            "select=id,gr_number,received_at,notes,purchase_orders(po_number),goods_receipt_lines(id,product_id,batch_number,quantity_kg,quantity_received,condition,products(name,sku))" +
                "&id=eq.${enc(grId)}&limit=1",
        ).optJSONObject(0)

    /** Terima barang: buat GR + lines. Memakai RPC bila ada, jika tidak insert biasa. */
    fun createGoodsReceipt(
        organizationId: String,
        poId: String?,
        notes: String?,
        receivedBy: String?,
        lines: List<GrLine>,
    ): Result<String> = runCatching {
        val grNumber = generateNumber("GR")
        val gr = JSONObject()
            .put("organization_id", organizationId)
            .put("gr_number", grNumber)
            .put("received_at", nowIso())
            .apply { if (poId != null) put("po_id", poId) }
            .apply { if (!notes.isNullOrBlank()) put("notes", notes) }
            .apply { if (receivedBy != null) put("received_by", receivedBy) }
        val grId = api.insert("goods_receipts", gr)

        val arr = JSONArray()
        lines.forEach { l ->
            arr.put(
                JSONObject()
                    .put("gr_id", grId)
                    .put("product_id", l.productId)
                    .put("quantity_kg", l.quantityKg)
                    .put("quantity_received", l.quantityKg)
                    .put("condition", l.condition)
                    .apply { if (!l.batchNumber.isNullOrBlank()) put("batch_number", l.batchNumber) },
            )
        }
        try {
            api.insertMany("goods_receipt_lines", arr)
        } catch (e: Exception) {
            // Batalkan GR yatim supaya tidak ada dokumen tanpa isi.
            runCatching { api.delete("goods_receipts", "id=eq.$grId") }
            throw e
        }
        grId
    }.recoverCatching { throw it }

    // ---------- Goods Issue (manual, tanpa dokumen) ----------

    /**
     * Hitung stok baru lalu update dengan guard `quantity_kg >= take`.
     * Guard mencegah dua pengeluaran bersamaan membuat stok negatif.
     */
    fun decreaseStock(inventoryId: String, currentQty: Double, take: Double): Double {
        require(take > 0) { "Jumlah harus > 0." }
        require(take <= currentQty) { "Stok hanya $currentQty kg." }
        val newQty = currentQty - take
        val rows = api.updateReturning(
            "inventory",
            "id=eq.${enc(inventoryId)}&quantity_kg=gte.$take",
            JSONObject().put("quantity_kg", newQty),
        )
        if (rows.length() == 0) throw Exception("Stok berubah atau tidak cukup. Muat ulang lalu coba lagi.")
        return newQty
    }

    /**
     * Kembalikan stok ke nilai sebelumnya. Dipakai rollback bila pencatatan
     * movement gagal, supaya stok tidak berkurang tanpa jejak.
     */
    fun addStock(inventoryId: String, restoreTo: Double) {
        api.updateReturning("inventory", "id=eq.${enc(inventoryId)}", JSONObject().put("quantity_kg", restoreTo))
    }

    fun recordMovement(
        organizationId: String,
        type: String,
        productId: String,
        batchNumber: String?,
        quantityKg: Double,
        fromLocation: String?,
        referenceType: String,
        referenceId: String?,
        notes: String?,
        performedBy: String?,
    ): Unit {
        val movement = JSONObject()
            .put("organization_id", organizationId)
            .put("movement_type", type)
            .put("product_id", productId)
            .put("quantity_kg", quantityKg)
            .put("reference_type", referenceType)
            .apply { if (fromLocation != null) put("from_location", fromLocation) }
            .apply { if (referenceId != null) put("reference_id", referenceId) }
            .apply { if (!batchNumber.isNullOrBlank()) put("batch_number", batchNumber) }
            .apply { if (!notes.isNullOrBlank()) put("notes", notes) }
            .apply { if (performedBy != null) put("performed_by", performedBy) }
        api.insert("inventory_movements", movement)
    }

    // ---------- QC ----------

    fun qcInspections(limit: Int = 100): JSONArray =
        api.get(
            "qc_inspections",
            "select=id,status,inspection_type,inspected_at,notes,photo_url,inspector_name,goods_receipts(gr_number),delivery_orders(do_number),qc_inspection_lines(id,condition,item_name,batch_number,quantity_kg,photo_url,category,notes)" +
                "&order=inspected_at.desc&limit=$limit",
        )

    fun createQcInspection(
        organizationId: String,
        grId: String?,
        deliveryOrderId: String?,
        inspectionType: String,
        inspectorUserId: String,
        inspectorName: String?,
        notes: String?,
        lines: List<QcLine>,
    ): Result<String> = runCatching {
        val head = JSONObject()
            .put("organization_id", organizationId)
            .put("inspection_type", inspectionType)
            .put("inspector_user_id", inspectorUserId)
            .put("status", "COMPLETED")
            .put("inspected_at", nowIso())
            .apply { if (grId != null) put("gr_id", grId) }
            .apply { if (deliveryOrderId != null) put("delivery_order_id", deliveryOrderId) }
            .apply { if (!inspectorName.isNullOrBlank()) put("inspector_name", inspectorName) }
            .apply { if (!notes.isNullOrBlank()) put("notes", notes) }
        val id = api.insert("qc_inspections", head)

        val arr = JSONArray()
        lines.forEach { l ->
            arr.put(
                JSONObject()
                    .put("inspection_id", id)
                    .put("gr_line_id", l.grLineId)
                    .put("condition", l.condition)
                    .put("quantity_kg", l.quantityKg)
                    .put("checklist", l.checklist ?: JSONObject())
                    .apply { if (!l.notes.isNullOrBlank()) put("notes", l.notes) }
                    .apply { if (!l.itemName.isNullOrBlank()) put("item_name", l.itemName) },
            )
        }
        if (arr.length() > 0) {
            try {
                api.insertMany("qc_inspection_lines", arr)
            } catch (e: Exception) {
                runCatching { api.delete("qc_inspections", "id=eq.$id") }
                throw e
            }
        }
        id
    }.recoverCatching { throw it }

    // ---------- Waste ----------

    fun wasteRecords(limit: Int = 100): JSONArray =
        api.get(
            "waste_records",
            "select=id,quantity_kg,reason,notes,recorded_at,products(name,sku),rental_contracts(contract_number)&order=recorded_at.desc&limit=$limit",
        )

    fun stockWaste(limit: Int = 100): JSONArray =
        api.get(
            "stock_waste",
            "select=id,quantity_kg,reason,notes,recorded_at,products(name,sku)&order=recorded_at.desc&limit=$limit",
        )

    fun recordStockWaste(
        organizationId: String,
        productId: String,
        batchNumber: String?,
        quantityKg: Double,
        reason: String,
        notes: String?,
        recordedBy: String?,
    ): Result<Unit> = runCatching {
        val body = JSONObject()
            .put("organization_id", organizationId)
            .put("product_id", productId)
            .put("quantity_kg", quantityKg)
            .put("reason", reason)
            .put("recorded_at", nowIso())
            .apply { if (!batchNumber.isNullOrBlank()) put("batch_number", batchNumber) }
            .apply { if (!notes.isNullOrBlank()) put("notes", notes) }
            .apply { if (recordedBy != null) put("recorded_by", recordedBy) }
        api.insert("stock_waste", body)
    }

    // ---------- Surat Jalan ----------

    fun deliveryRequests(status: String? = null, limit: Int = 100): JSONArray {
        val q = buildString {
            append("select=id,status,notes,created_at,customer_id,delivery_date,driver_name,vehicle_number,destination,prepared_at,")
            append("delivery_request_lines(id,product_id,item_name,quantity_kg,notes)")
            if (!status.isNullOrBlank()) append("&status=eq.${enc(status)}")
            append("&order=created_at.desc&limit=$limit")
        }
        return api.get("delivery_requests", q)
    }

    fun deliveryOrders(limit: Int = 100): JSONArray =
        api.get(
            "delivery_orders",
            "select=id,do_number,status,delivery_date,destination,driver_name,vehicle_number,notes,created_at,printed_at," +
                "delivery_order_lines(id,item_name,quantity_kg,unit,notes,batch_number,products(name,sku))&order=created_at.desc&limit=$limit",
        )

    fun deliveryOrderDetail(doId: String): JSONObject? =
        api.get(
            "delivery_orders",
            "select=id,do_number,status,delivery_date,destination,driver_name,vehicle_number,notes,created_at,printed_at,released_at," +
                "delivery_order_lines(id,item_name,quantity_kg,unit,notes,batch_number,products(name,sku))&id=eq.${enc(doId)}&limit=1",
        ).optJSONObject(0)

    fun prepareDeliveryRequest(reqId: String, userId: String?): Result<Unit> = runCatching {
        val body = JSONObject()
            .put("status", "PREPARED")
            .put("prepared_at", nowIso())
            .apply { if (userId != null) put("prepared_by", userId) }
        val rows = api.updateReturning("delivery_requests", "id=eq.${enc(reqId)}", body)
        if (rows.length() == 0) throw Exception("Permintaan tidak ditemukan atau tidak boleh diubah.")
    }

    fun generateNumber(prefix: String): String {
        val t = api.rpc("generate_number", JSONObject().put("p_prefix", prefix))
        val v = t.nextValue()
        return when (v) {
            is String -> v
            is JSONArray -> v.optString(0)
            is JSONObject -> v.optString("value", v.optString("generate_number"))
            else -> ""
        }
    }

    private fun enc(s: String): String = Query.enc(s)

    companion object {
        fun nowIso(): String {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
            fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
            return fmt.format(java.util.Date())
        }
    }

    data class GrLine(
        val productId: String,
        val quantityKg: Double,
        val batchNumber: String? = null,
        val condition: String = "GOOD",
    )

    data class QcLine(
        val grLineId: String,
        val condition: String,
        val quantityKg: Double,
        val itemName: String? = null,
        val notes: String? = null,
        val checklist: JSONObject? = null,
    )
}
