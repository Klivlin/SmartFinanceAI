package ru.vogu.smartfinance.presentation.scanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Экран сканера чеков — CLAUDE.md §4/§5.2. Одновременно слушает кадры на QR-код (ML Kit
 * Barcode Scanning, непрерывно через [ImageAnalysis]) и предоставляет кнопку "Сфотографировать
 * чек" для OCR-пути (ML Kit Text Recognition через [ImageCapture]) — оба пути доступны на одном
 * экране без переключения режимов, что и предполагает §4 (QR — предпочтительный, полностью
 * офлайн-путь; фото — запасной, для чеков без читаемого QR).
 *
 * ВАЖНО (проверено при реализации Шага 5): используемая версия ML Kit Text Recognition v2
 * (`com.google.mlkit:text-recognition:16.0.1`, "Latin"-модель — единственная поддерживаемая
 * офлайн без доп. модулей) официально не имеет отдельной модели для кириллицы — на момент
 * написания кода это открытый feature request в трекере ML Kit, а не готовая функция. Поэтому
 * OCR-путь надёжно распознаёт цифры (суммы) в позициях чека, но распознавание названий товаров
 * на русском может быть неточным. Локальный фильтр [ru.vogu.smartfinance.data.parser.ReceiptOcrParser]
 * ищет строки по цифровому паттерну цены (не зависит от языка), поэтому позиции всё равно
 * находятся; Gemini на Tier 3 может докомпенсировать частично искажённые названия по контексту,
 * но 100% точность названий на OCR-пути не гарантирована. QR-путь этой проблемы не имеет:
 * данные там — обычная ASCII key=value строка, а не распознанное изображение.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) viewModel.onIntent(ScannerIntent.CameraPermissionDenied)
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ScannerEffect.NavigateBack -> onNavigateBack()
            }
        }
    }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
        viewModel.onIntent(ScannerIntent.ErrorMessageShown)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сканировать чек") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onIntent(ScannerIntent.CloseRequested) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (hasCameraPermission) {
                ScannerCameraContent(
                    statusHint = state.statusHint,
                    isProcessing = state.isProcessing,
                    onBarcodeDetected = { viewModel.onIntent(ScannerIntent.BarcodeDetected(it)) },
                    onPhotoRecognized = { viewModel.onIntent(ScannerIntent.PhotoRecognized(it)) },
                    onPhotoCaptureFailed = { viewModel.onIntent(ScannerIntent.PhotoCaptureFailed) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                PermissionRationale(
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun ScannerCameraContent(
    statusHint: String,
    isProcessing: Boolean,
    onBarcodeDetected: (String) -> Unit,
    onPhotoRecognized: (List<String>) -> Unit,
    onPhotoCaptureFailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // Аналайзер живёт всё время жизни составного компонента (LaunchedEffect ниже запускается
    // один раз), а isProcessing/колбэки меняются при каждой рекомпозиции — без rememberUpdatedState
    // аналайзер держал бы значения ПЕРВОЙ композиции и никогда не увидел бы актуальный isProcessing.
    val isProcessingState = rememberUpdatedState(isProcessing)
    val onBarcodeDetectedState = rememberUpdatedState(onBarcodeDetected)

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            barcodeScanner.close()
            textRecognizer.close()
        }
    }

    LaunchedEffect(previewView) {
        val cameraProvider = context.awaitCameraProvider()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { useCase ->
                useCase.setAnalyzer(cameraExecutor) { imageProxy ->
                    analyzeFrameForBarcode(imageProxy, barcodeScanner, isProcessingState, onBarcodeDetectedState)
                }
            }

        val capture = ImageCapture.Builder().build()
        imageCapture = capture

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
                capture
            )
        } catch (e: IllegalStateException) {
            // Камера недоступна (занята другим приложением, отсутствует на устройстве и т.п.) —
            // предпросмотр останется пустым, но кнопка "Закрыть" в TopAppBar всё ещё работает.
        } catch (e: IllegalArgumentException) {
            // Устройство не поддерживает запрошенную комбинацию use case'ов.
        }
    }

    Box(modifier = modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = statusHint,
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    val capture = imageCapture ?: return@Button
                    capturePhotoForOcr(capture, cameraExecutor, textRecognizer, onPhotoRecognized, onPhotoCaptureFailed)
                },
                enabled = !isProcessing && imageCapture != null
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Сфотографировать чек")
            }
        }

        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@Composable
private fun PermissionRationale(onRequestPermission: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Нужен доступ к камере, чтобы сканировать QR-код чека или сфотографировать его.",
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestPermission) { Text("Разрешить доступ к камере") }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            )
        }) {
            Text("Открыть настройки приложения")
        }
    }
}

/** Непрерывный анализ кадров на QR-код — CLAUDE.md §4.1. Закрывать [imageProxy] обязательно в любом исходе, иначе пайплайн CameraX встанет. */
@OptIn(ExperimentalGetImage::class)
private fun analyzeFrameForBarcode(
    imageProxy: ImageProxy,
    barcodeScanner: BarcodeScanner,
    isProcessing: State<Boolean>,
    onBarcodeDetected: State<(String) -> Unit>
) {
    val mediaImage = imageProxy.image
    if (isProcessing.value || mediaImage == null) {
        imageProxy.close()
        return
    }

    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    barcodeScanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            val rawValue = barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue
            if (rawValue != null) {
                onBarcodeDetected.value(rawValue)
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

/**
 * Снимок для OCR-пути — CLAUDE.md §4.2. Декодируем через [BitmapFactory], а не
 * `InputImage.fromMediaImage`: [ImageCapture] отдаёт JPEG-байты, а не гарантированный
 * YUV_420_888 кадр (в отличие от [ImageAnalysis] выше), и именно так рекомендует делать
 * официальная интеграция ML Kit + CameraX ImageCapture.
 */
private fun capturePhotoForOcr(
    imageCapture: ImageCapture,
    executor: Executor,
    textRecognizer: TextRecognizer,
    onPhotoRecognized: (List<String>) -> Unit,
    onPhotoCaptureFailed: () -> Unit
) {
    imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            try {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                if (bitmap == null) {
                    image.close()
                    onPhotoCaptureFailed()
                    return
                }

                val inputImage = InputImage.fromBitmap(bitmap, image.imageInfo.rotationDegrees)
                textRecognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val lines = visionText.textBlocks.flatMap { block -> block.lines.map { it.text } }
                        onPhotoRecognized(lines)
                    }
                    .addOnFailureListener {
                        onPhotoCaptureFailed()
                    }
                    .addOnCompleteListener {
                        image.close()
                    }
            } catch (e: Exception) {
                image.close()
                onPhotoCaptureFailed()
            }
        }

        override fun onError(exception: ImageCaptureException) {
            onPhotoCaptureFailed()
        }
    })
}

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            { continuation.resume(future.get()) },
            ContextCompat.getMainExecutor(this)
        )
    }
