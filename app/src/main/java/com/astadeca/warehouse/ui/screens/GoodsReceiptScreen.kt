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
import com.astadeca.warehouse.ui.components.fmtDateTime
import com.astadeca.warehouse.ui.components.fmtKg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun GoodsReceiptScreen(vm: AppViewModel, onBack: () -> Unit) {
    var rows by remember { mutableStateOf<JSONArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        rows = null; error = null
        try {
            rows = withContext(Dispatchers.IO) { vm.repo.goodsReceipts() }
        } catch (e: Exception) {
            error = e.message; rows = JSONArray()
        }
    }

    ScreenScaffold(
        title = "Terima Barang",
        onBack = onBack,
        actions = { IconButton(onClick = { reload++ }) { Icon(Icons.Default.Refresh, contentDescription = "Muat ulang") } },
    ) { mod ->
        Column(mod.fillMaxSize()) {
            ErrorBanner(error)
            val data = rows
            when {
                data == null -> LoadingBox()
                data.length() == 0 -> EmptyState("Belum ada Goods Receipt.")
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(data.length()) { i -> GrCard(data.getJSONObject(i)) }
                }
            }
        }
    }
}

@Composable
private fun GrCard(gr: JSONObject) {
    val lines = gr.optJSONArray("goods_receipt_lines") ?: JSONArray()
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(gr.optString("gr_number"), fontWeight = FontWeight.SemiBold)
                Text(fmtDateTime(gr.optString("received_at")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            gr.optJSONObject("purchase_orders")?.optString("po_number")?.takeIf { it.isNotBlank() }?.let {
                Text("PO $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            for (i in 0 until lines.length()) {
                val l = lines.getJSONObject(i)
                val name = l.optJSONObject("products")?.optString("name") ?: "-"
                Text(
                    "• $name — ${fmtKg(l.optString("quantity_received").ifBlank { l.optString("quantity_kg") })} kg" +
                        (l.optString("batch_number").takeIf { it.isNotBlank() }?.let { " · Batch $it" } ?: "") +
                        (l.optString("condition").takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
