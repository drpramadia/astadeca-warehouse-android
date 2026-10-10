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

    // ---------- Baskets ----------

    fun baskets(search: String = "", limit: Int = 200): JSONArray {
        val q = buildString {
            append("select=id,code,capacity_kg,status,zone_id,cold_storage_zones(name,cold_storages(name))")
            if (search.isNotBlank()) append("&code=ilike.*${enc(search)}*")
            append("&order=code.asc&limit=$limit")
        }
        return api.get("cold_storage_baskets", q)
    }

    // ---------- Goods Receipt ----------

    fun goodsReceipts(limit: Int = 100): JSONArray =
        api.get(
            "goods_receipts",
            "select=id,gr_number,received_at,notes,purchase_orders(po_number),goods_receipt_lines(id,product_id,batch_number,quantity_kg,quantity_received,condition,products(name,sku),delivery_order_id)" +
                "&order=received_at.desc&limit=$limit",
        )

    // ---------- Goods Issue (manual, tanpa dokumen) ----------

    /**
     * Keluarkan stok lewat RPC `issue_stock` — satu transaksi di server:
     * lock baris, cek izin, guard stok, update, catat movement. Tidak ada
     * jendela di mana stok berkurang tanpa jejak movement.
     */
    fun issueStock(inventoryId: String, take: Double, notes: String?, performedBy: String?): Double {
        require(take > 0) { "Jumlah harus > 0." }
        val params = JSONObject()
            .put("p_inventory_id", inventoryId)
            .put("p_take", take)
            .put("p_reference_type", "MANUAL")
            .apply { if (!notes.isNullOrBlank()) put("p_notes", notes) }
            .apply { if (performedBy != null) put("p_performed_by", performedBy) }
        val v = api.rpc("issue_stock", params).nextValue()
        val obj = when (v) {
            is JSONObject -> v
            is JSONArray -> if (v.length() > 0) v.getJSONObject(0) else JSONObject()
            else -> JSONObject()
        }
        if (!obj.optBoolean("success", false)) {
            throw Exception(obj.optString("message").ifBlank { "Pengeluaran stok gagal." })
        }
        return obj.optDouble("quantity_kg", 0.0)
    }

    // ---------- QC ----------

    fun qcInspections(limit: Int = 100): JSONArray =
        api.get(
            "qc_inspections",
            "select=id,status,inspection_type,inspected_at,notes,photo_url,inspector_name,goods_receipts(gr_number),delivery_orders(do_number),qc_inspection_lines(id,condition,item_name,batch_number,quantity_kg,photo_url,category,notes)" +
                "&order=inspected_at.desc&limit=$limit",
        )

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

    private fun enc(s: String): String = Query.enc(s)

    companion object {
        fun nowIso(): String {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
            fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
            return fmt.format(java.util.Date())
        }
    }
}
