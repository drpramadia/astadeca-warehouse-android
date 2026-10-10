package com.astadeca.warehouse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.astadeca.warehouse.ui.Routes
import com.astadeca.warehouse.ui.SessionInfo

private data class MenuItem(val label: String, val desc: String, val icon: ImageVector, val route: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    session: SessionInfo?,
    onOpen: (String) -> Unit,
    onLogout: () -> Unit,
) {
    val menus = listOf(
        MenuItem("Inventory", "Lihat stok per batch & basket", Icons.Default.Inventory2, Routes.INVENTORY),
        MenuItem("Terima Barang", "Goods Receipt dari PO", Icons.Default.MoveToInbox, Routes.GR),
        MenuItem("Keluar Barang", "Goods Issue manual", Icons.Default.Outbox, Routes.GI),
        MenuItem("Basket", "Daftar & tambah basket", Icons.Default.ShoppingCart, Routes.BASKETS),
        MenuItem("QC", "Pemeriksaan barang masuk/keluar", Icons.Default.Science, Routes.QC),
        MenuItem("Waste", "Catat barang rusak/kedaluwarsa", Icons.Default.DeleteSweep, Routes.WASTE),
        MenuItem("Surat Jalan", "Siapkan & lihat DO", Icons.Default.LocalShipping, Routes.SURAT_JALAN),
        MenuItem("Scan", "Cari barang/basket via QR", Icons.Default.QrCode2, Routes.scanner("product")),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Warehouse") },
                actions = {
                    IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Keluar") }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column {
                    Text(session?.fullName ?: "Pengguna", style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(session?.roleName, session?.organizationName).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
            items(menus) { m ->
                Card(
                    onClick = { onOpen(m.route) },
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(m.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.size(14.dp))
                        Column {
                            Text(m.label, style = MaterialTheme.typography.titleSmall)
                            Text(m.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
