package com.astadeca.warehouse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.astadeca.warehouse.ui.ScannerBus
import com.astadeca.warehouse.ui.ScreenScaffold
import com.astadeca.warehouse.ui.components.EmptyState
import com.astadeca.warehouse.ui.components.ErrorBanner
import com.astadeca.warehouse.ui.components.fmtKg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun BasketScreen(vm: AppViewModel, onBack: () -> Unit, onScan: () -> Unit) {
    var rows by remember { mutableStateOf<JSONArray?>(null) }
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload, query) {
        rows = null; error = null
        try {
            rows = withContext(Dispatchers.IO) { vm.repo.baskets(search = query) }
        } catch (e: Exception) {
            error = e.message; rows = JSONArray()
        }
    }

    DisposableEffect(Unit) {
        ScannerBus.subscribe("basket") { value -> query = value }
        onDispose { ScannerBus.unsubscribe("basket") }
    }

    ScreenScaffold(
        title = "Basket",
        onBack = onBack,
        actions = {
            IconButton(onClick = onScan) { Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan") }
            IconButton(onClick = { reload++ }) { Icon(Icons.Default.Refresh, contentDescription = "Muat ulang") }
        },
    ) { mod ->
        Column(mod.fillMaxSize()) {
            ErrorBanner(error)
            val data = rows
            when {
                data == null -> LoadingBox()
                data.length() == 0 -> EmptyState("Tidak ada basket.")
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(data.length()) { i -> BasketCard(data.getJSONObject(i)) }
                }
            }
        }
    }
}

@Composable
private fun BasketCard(b: JSONObject) {
    val zone = b.optJSONObject("cold_storage_zones")
    val storage = zone?.optJSONObject("cold_storages")
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(b.optString("code"), fontWeight = FontWeight.SemiBold)
                Text(b.optString("status"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                listOfNotNull(
                    storage?.optString("name")?.takeIf { it.isNotBlank() },
                    zone?.optString("name")?.takeIf { it.isNotBlank() },
                    "Kapasitas ${fmtKg(b.optString("capacity_kg"))} kg",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
