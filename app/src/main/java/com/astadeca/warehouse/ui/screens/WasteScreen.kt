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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import com.astadeca.warehouse.ui.components.fmtDateTime
import com.astadeca.warehouse.ui.components.fmtKg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun WasteScreen(vm: AppViewModel, onBack: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var rows by remember { mutableStateOf<JSONArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(tab, reload) {
        rows = null; error = null
        try {
            rows = withContext(Dispatchers.IO) {
                if (tab == 0) vm.repo.stockWaste() else vm.repo.wasteRecords()
            }
        } catch (e: Exception) {
            error = e.message; rows = JSONArray()
        }
    }

    ScreenScaffold(
        title = "Waste",
        onBack = onBack,
        actions = { IconButton(onClick = { reload++ }) { Icon(Icons.Default.Refresh, contentDescription = "Muat ulang") } },
    ) { mod ->
        Column(mod.fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Stok Rusak") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Kontrak Sewa") })
            }
            ErrorBanner(error)
            val data = rows
            when {
                data == null -> LoadingBox()
                data.length() == 0 -> EmptyState("Belum ada catatan waste.")
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(data.length()) { i -> WasteCard(data.getJSONObject(i)) }
                }
            }
        }
    }
}

@Composable
private fun WasteCard(w: JSONObject) {
    val product = w.optJSONObject("products")
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(product?.optString("name") ?: "-", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Text("${fmtKg(w.optString("quantity_kg"))} kg", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
            Text(
                listOfNotNull(
                    w.optString("reason").takeIf { it.isNotBlank() },
                    w.optJSONObject("rental_contracts")?.optString("contract_number")?.takeIf { it.isNotBlank() },
                    fmtDateTime(w.optString("recorded_at")),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            w.optString("notes").takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
