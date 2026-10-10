package com.astadeca.warehouse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astadeca.warehouse.ui.AppViewModel
import com.astadeca.warehouse.ui.LoadingBox
import com.astadeca.warehouse.ui.ScannerBus
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
fun InventoryScreen(vm: AppViewModel, onBack: () -> Unit, onScan: () -> Unit) {
    var rows by remember { mutableStateOf<JSONArray?>(null) }
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey, query) {
        rows = null
        error = null
        try {
            rows = withContext(Dispatchers.IO) { vm.repo.inventory(search = query) }
        } catch (e: Exception) {
            error = e.message
            rows = JSONArray()
        }
    }

    // Terima hasil scan: isi kolom pencarian.
    DisposableEffect(Unit) {
        ScannerBus.subscribe("inventory") { value -> query = value }
        onDispose { ScannerBus.unsubscribe("inventory") }
    }

    ScreenScaffold(
        title = "Inventory",
        onBack = onBack,
        actions = {
            IconButton(onClick = onScan) { Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan") }
            IconButton(onClick = { reloadKey++ }) { Icon(Icons.Default.Refresh, contentDescription = "Muat ulang") }
        },
    ) { mod ->
        Column(mod.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("Cari nama / SKU / batch") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ErrorBanner(error)

            val data = rows
            when {
                data == null -> LoadingBox()
                data.length() == 0 -> EmptyState("Tidak ada stok yang cocok.")
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(data.length()) { i ->
                        val row = data.getJSONObject(i)
                        InventoryRow(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryRow(row: JSONObject) {
    val product = row.optJSONObject("products")
    val basket = row.optJSONObject("cold_storage_baskets")
    val zone = basket?.optJSONObject("cold_storage_zones")
    val storage = zone?.optJSONObject("cold_storages")

    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(product?.optString("name") ?: "-", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Text("${fmtKg(row.optString("quantity_kg"))} kg", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                listOfNotNull(
                    product?.optString("sku")?.takeIf { it.isNotBlank() }?.let { "SKU $it" },
                    row.optString("batch_number").takeIf { it.isNotBlank() }?.let { "Batch $it" },
                ).joinToString(" · ").ifBlank { "-" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                listOfNotNull(
                    row.optString("status").takeIf { it.isNotBlank() },
                    basket?.optString("code")?.takeIf { it.isNotBlank() },
                    storage?.optString("name")?.takeIf { it.isNotBlank() },
                    row.optString("expiry_date").takeIf { it.isNotBlank() }?.let { "Exp ${fmtDate(it)}" },
                ).joinToString(" · ").ifBlank { "-" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
