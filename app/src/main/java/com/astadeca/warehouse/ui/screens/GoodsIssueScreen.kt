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
import com.astadeca.warehouse.ui.components.fmtKg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun GoodsIssueScreen(vm: AppViewModel, onBack: () -> Unit) {
    var rows by remember { mutableStateOf<JSONArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        rows = null; error = null
        try {
            rows = withContext(Dispatchers.IO) { vm.repo.inventory() }
        } catch (e: Exception) {
            error = e.message; rows = JSONArray()
        }
    }

    ScreenScaffold(
        title = "Keluar Barang",
        onBack = onBack,
        actions = { IconButton(onClick = { reload++ }) { Icon(Icons.Default.Refresh, contentDescription = "Muat ulang") } },
    ) { mod ->
        Column(mod.fillMaxSize()) {
            ErrorBanner(error)
            val data = rows
            when {
                data == null -> LoadingBox()
                data.length() == 0 -> EmptyState("Tidak ada stok untuk dikeluarkan.")
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(data.length()) { i -> IssueRow(vm, data.getJSONObject(i), onIssueError = { error = it }) { reload++ } }
                }
            }
        }
    }
}

@Composable
private fun IssueRow(
    vm: AppViewModel,
    row: JSONObject,
    onIssueError: (String) -> Unit,
    onDone: () -> Unit,
) {
    val product = row.optJSONObject("products")
    val qty = row.optString("quantity_kg").toDoubleOrNull() ?: 0.0
    val batch = row.optString("batch_number").takeIf { it.isNotBlank() }
    var value by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(product?.optString("name") ?: "-", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Text("${fmtKg(qty)} kg", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                listOfNotNull(product?.optString("sku")?.takeIf { it.isNotBlank() }, batch?.let { "Batch $it" }).joinToString(" · ").ifBlank { "-" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Jumlah keluar (kg)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.Button(
                    enabled = !busy && (value.toDoubleOrNull() ?: 0.0) > 0,
                    onClick = {
                        val take = value.toDoubleOrNull() ?: 0.0
                        if (take <= 0) return@Button
                        busy = true
                        vm.runIo(onIssueError) {
                            try {
                                val session = vm.sessionOrNull()
                                withContext(Dispatchers.IO) {
                                    // Satu RPC transaksional: lock + guard + update + movement.
                                    vm.repo.issueStock(
                                        inventoryId = row.optString("id"),
                                        take = take,
                                        notes = "Pengeluaran manual (APK warehouse)",
                                        performedBy = session?.userId,
                                    )
                                }
                                value = ""
                                onDone()
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.padding(top = 6.dp),
                ) { if (busy) androidx.compose.material3.CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp) else Text("Keluarkan") }
            }
        }
    }
}
