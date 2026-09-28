package ru.vogu.smartfinance.presentation.scanner

/**
 * MVI-контракт экрана сканера чеков — CLAUDE.md §5.2/§6. Камера и ML Kit — Android-специфика,
 * поэтому вся работа с CameraX/распознавателями остаётся в [ScannerScreen]; сюда попадают уже
 * "очищенные" данные (сырое значение QR или список распознанных строк текста), а бизнес-логику
 * их разбора и сохранения выполняет [ru.vogu.smartfinance.domain.usecase.ParseReceiptUseCase]
 * через [ScannerViewModel].
 */
sealed interface ScannerIntent {
    data class BarcodeDetected(val rawValue: String) : ScannerIntent
    data class PhotoRecognized(val lines: List<String>) : ScannerIntent
    data object PhotoCaptureFailed : ScannerIntent
    data object CameraPermissionDenied : ScannerIntent
    data object ErrorMessageShown : ScannerIntent
    data object CloseRequested : ScannerIntent
}

data class ScannerState(
    val isProcessing: Boolean = false,
    val statusHint: String = "Наведите камеру на QR-код чека или сфотографируйте его целиком",
    val errorMessage: String? = null
)

sealed interface ScannerEffect {
    data object NavigateBack : ScannerEffect
}
