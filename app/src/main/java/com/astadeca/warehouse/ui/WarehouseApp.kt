package com.astadeca.warehouse.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.astadeca.warehouse.ui.screens.BasketScreen
import com.astadeca.warehouse.ui.screens.GoodsIssueScreen
import com.astadeca.warehouse.ui.screens.GoodsReceiptScreen
import com.astadeca.warehouse.ui.screens.InventoryScreen
import com.astadeca.warehouse.ui.screens.LoginScreen
import com.astadeca.warehouse.ui.screens.QcScreen
import com.astadeca.warehouse.ui.screens.SuratJalanScreen
import com.astadeca.warehouse.ui.screens.WasteScreen
import com.astadeca.warehouse.ui.screens.HomeScreen
import com.astadeca.warehouse.ui.screens.ScannerScreen

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val INVENTORY = "inventory"
    const val GR = "gr"
    const val GI = "gi"
    const val BASKETS = "baskets"
    const val QC = "qc"
    const val WASTE = "waste"
    const val SURAT_JALAN = "surat-jalan"
    const val SCANNER = "scanner/{mode}"
    fun scanner(mode: String) = "scanner/$mode"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarehouseApp(vm: AppViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    // Tunggu bootstrap selesai sebelum memutuskan layar awal, supaya sesi
    // tersimpan tidak "tersentak" ke login lebih dulu.
    if (state is AppState.Loading) {
        LoadingBox()
        return
    }

    val session = (state as? AppState.LoggedIn)?.session
    val nav = rememberNavController()

    // Sesi berubah (login/logout/expired) -> pindahkan layar.
    // Login sukses mengubah state, bukan sinyal UI; sesi mati memaksa kembali ke login.
    val loggedIn = session != null
    LaunchedEffect(loggedIn) {
        val route = nav.currentDestination?.route
        if (loggedIn && route == Routes.LOGIN) {
            nav.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
        } else if (!loggedIn && route != Routes.LOGIN) {
            nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
        }
    }

    NavHost(
        navController = nav,
        startDestination = if (session != null) Routes.HOME else Routes.LOGIN,
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                busy = state is AppState.Loading,
                onLogin = { u, p, onError -> vm.login(u, p, onError) },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                session = session,
                onOpen = { route -> nav.navigate(route) },
                onLogout = {
                    vm.logout()
                    nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                },
            )
        }

        composable(Routes.INVENTORY) {
            InventoryScreen(vm, onBack = { nav.popBackStack() }, onScan = { nav.navigate(Routes.scanner("inventory")) })
        }
        composable(Routes.GR) {
            GoodsReceiptScreen(vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.GI) {
            GoodsIssueScreen(vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.BASKETS) {
            BasketScreen(vm, onBack = { nav.popBackStack() }, onScan = { nav.navigate(Routes.scanner("basket")) })
        }
        composable(Routes.QC) { QcScreen(vm, onBack = { nav.popBackStack() }) }
        composable(Routes.WASTE) { WasteScreen(vm, onBack = { nav.popBackStack() }) }
        composable(Routes.SURAT_JALAN) { SuratJalanScreen(vm, onBack = { nav.popBackStack() }) }

        composable(Routes.SCANNER) { entry ->
            val mode = entry.arguments?.getString("mode") ?: "product"
            ScannerScreen(
                onBack = { nav.popBackStack() },
                onResult = { value ->
                    ScannerBus.publish(value, mode)
                    nav.popBackStack()
                },
            )
        }
    }
}

/** Saluran sederhana: hasil scan diteruskan kembali ke layar yang membukanya. */
object ScannerBus {
    private val listeners = mutableMapOf<String, (String) -> Unit>()
    fun subscribe(mode: String, fn: (String) -> Unit) { listeners[mode] = fn }
    fun unsubscribe(mode: String) { listeners.remove(mode) }
    fun publish(value: String, mode: String) { listeners[mode]?.invoke(value) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                    }
                },
                actions = { actions() },
            )
        },
    ) { pad ->
        content(Modifier.padding(pad))
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
