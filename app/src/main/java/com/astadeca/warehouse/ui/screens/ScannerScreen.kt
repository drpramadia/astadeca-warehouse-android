package com.astadeca.warehouse.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.astadeca.warehouse.ui.ScreenScaffold
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(onBack: () -> Unit, onResult: (String) -> Unit) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var hint by remember { mutableStateOf("Arahkan kamera ke barcode / QR") }
    var handled by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) hint = "Izin kamera ditolak. Buka Pengaturan untuk mengizinkan."
    }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    ScreenScaffold(title = "Scan", onBack = onBack) { mod ->
        Box(mod.fillMaxSize()) {
            if (granted) {
                CameraPreview(
                    onCode = { value ->
                        if (!handled) {
                            handled = true
                            onResult(value)
                        }
                    },
                )
            }
            Text(
                hint,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CameraPreview(onCode: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_DATA_MATRIX,
                    Barcode.FORMAT_PDF417,
                    Barcode.FORMAT_AZTEC,
                )
                .build(),
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { c ->
            val previewView = PreviewView(c)
            val providerFuture = ProcessCameraProvider.getInstance(c)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { ia ->
                        ia.setAnalyzer(executor) { proxy -> analyze(proxy, scanner, onCode) }
                    }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, ContextCompat.getMainExecutor(c))
            previewView
        },
    )
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun analyze(proxy: ImageProxy, scanner: com.google.mlkit.vision.barcode.BarcodeScanner, onCode: (String) -> Unit) {
    val media = proxy.image ?: run { proxy.close(); return }
    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { codes ->
            val value = codes.firstNotNullOfOrNull { it.rawValue?.takeIf { v -> v.isNotBlank() } }
            if (value != null) onCode(value)
        }
        .addOnCompleteListener { proxy.close() }
}
