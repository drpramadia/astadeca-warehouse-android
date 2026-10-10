package com.astadeca.warehouse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astadeca.warehouse.ui.AppViewModel
import com.astadeca.warehouse.ui.LoadingBox
import com.astadeca.warehouse.ui.ScreenScaffold
import com.astadeca.warehouse.ui.components.EmptyState
import com.astadeca.warehouse.ui.components.ErrorBanner
import com.astadeca.warehouse.ui.components.fmtDate
import com.astadeca.warehouse.ui.components.fmtKg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun SuratJalanScreen(vm: AppViewModel, onBack: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var rows by remember { mutableStateOf<JSONArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var preparing by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tab, reload) {
        rows = null; error = null
        try {
            rows = withContext(Dispatchers.IO) {
                if (tab == 0) vm.repo.deliveryRequests() else vm.repo.deliveryOrders()
            }
        } catch (e: Exception) {
            error = e.message; rows = JSONArray()
        }
    }

    ScreenScaffold(
        title = "Surat Jalan",
        onBack = onBack,
        actions = { IconButton(onClick = { reload++ }) { Icon(Icons.Default.Refresh, contentDescription = "Muat ulang") } },
    ) { mod ->
        Column(mod.fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Permintaan") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Surat Jalan / DO") })
            }
            ErrorBanner(error)
            val data = rows
            when {
                data == null -> LoadingBox()
                data.length() == 0 -> EmptyState(if (tab == 0) "Tidak ada permintaan." else "Belum ada surat jalan.")
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(data.length()) { i ->
                        val row = data.getJSONObject(i)
                        if (tab == 0) {
                            RequestCard(
                                row = row,
                                busy = preparing == row.optString("id"),
                                onPrepare = {
                                    val id = row.optString("id")
                                    preparing = id
                                    vm.runIo({ msg -> error = msg; preparing = null }) {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                vm.repo.prepareDeliveryRequest(id, vm.sessionOrNull()?.userId)
                                            }
                                            reload++
                                        } finally {
                                            preparing = null
                                        }
                                    }
                                },
                                onOpen = { detail = row },
                            )
                        } else {
                            DoCard(row = row, onOpen = { openDetail(vm, row.optString("id"), { detail = it }, { error = it }) })
                        }
                    }
                }
            }
        }
    }

    detail?.let { d ->
        AlertDialog(
            onDismissRequest = { detail = null },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("Tutup") } },
            title = { Text(d.optString("do_number").ifBlank { "Permintaan Surat Jalan" }) },
            text = { DetailBody(d) },
        )
    }
}

private fun openDetail(vm: AppViewModel, id: String, onOk: (JSONObject) -> Unit, onErr: (String) -> Unit) {
    vm.runIo(onErr) {
        val full = withContext(Dispatchers.IO) { vm.repo.deliveryOrderDetail(id) }
        if (full == null) onErr("Detail tidak ditemukan.") else onOk(full)
    }
}

@Composable
private fun RequestCard(row: JSONObject, busy: Boolean, onPrepare: () -> Unit, onOpen: () -> Unit) {
    val lines = row.optJSONArray("delivery_request_lines") ?: JSONArray()
    val status = row.optString("status")
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.optString("destination").ifBlank { "Permintaan" }, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                listOfNotNull(
                    row.optString("driver_name").takeIf { it.isNotBlank() }?.let { "Sopir $it" },
                    row.optString("vehicle_number").takeIf { it.isNotBlank() },
                    row.optString("delivery_date").takeIf { it.isNotBlank() }?.let { fmtDate(it) },
                ).joinToString(" · ").ifBlank { "-" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (i in 0 until lines.length()) {
                val l = lines.getJSONObject(i)
                Text("• ${l.optString("item_name")} — ${fmtKg(l.optString("quantity_kg"))} kg", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpen) { Text("Lihat") }
                if (status == "PENDING" || status == "APPROVED") {
                    TextButton(onClick = onPrepare, enabled = !busy) { Text(if (busy) "Menyiapkan…" else "Siapkan") }
                }
            }
        }
    }
}

@Composable
private fun DoCard(row: JSONObject, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.optString("do_number"), fontWeight = FontWeight.SemiBold)
                Text(row.optString("status"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                listOfNotNull(
                    row.optString("destination").takeIf { it.isNotBlank() },
                    row.optString("driver_name").takeIf { it.isNotBlank() }?.let { "Sopir $it" },
                    row.optString("delivery_date").takeIf { it.isNotBlank() }?.let { fmtDate(it) },
                ).joinToString(" · ").ifBlank { "-" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailBody(d: JSONObject) {
    val lines = d.optJSONArray("delivery_order_lines") ?: d.optJSONArray("delivery_request_lines") ?: JSONArray()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOfNotNull(
            d.optString("destination").takeIf { it.isNotBlank() }?.let { "Tujuan: $it" },
            d.optString("driver_name").takeIf { it.isNotBlank() }?.let { "Sopir: $it" },
            d.optString("vehicle_number").takeIf { it.isNotBlank() }?.let { "Kendaraan: $it" },
            d.optString("delivery_date").takeIf { it.isNotBlank() }?.let { "Tanggal: ${fmtDate(it)}" },
        ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text("Rincian:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        for (i in 0 until lines.length()) {
            val l = lines.getJSONObject(i)
            val name = l.optString("item_name").ifBlank { l.optJSONObject("products")?.optString("name") ?: "-" }
            Text("• $name — ${fmtKg(l.optString("quantity_kg"))} kg", style = MaterialTheme.typography.bodySmall)
        }
    }
}
