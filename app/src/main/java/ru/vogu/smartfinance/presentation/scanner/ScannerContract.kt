package ru.vogu.smartfinance.presentation.scanner

/**
 * MVI-контракт экрана сканера чеков — CLAUDE.md §5.2/§6. Камера и ML Kit — Android-специфика,
 * поэтому вся работа с CameraX/распознавателями остаётся в [ScannerScreen]; сюда попадают уже
 * "очищенные" данные (сырое значение QR или список распознанных строк текста), а бизнес-логику
 * их разбора и сохранения выполняет [ru.vogu.smartfinance.domain.usecase.ParseReceiptUseCase]
 * через [ScannerViewModel].
 */
sealed interface ScannerIntent {
    /**
     * QR только что обнаружен в кадре — до автоматического фото+OCR (см. [ReceiptScanned]),
     * который занимает заметное время (снимок + Gemini по сети). Без этого промежуточного
     * события экран весь этот период выглядел бы так, будто QR вообще не распознаётся: спиннер
     * и статус-текст обновлялись бы только по факту [ReceiptScanned], то есть уже ПОСЛЕ ожидания.
     */
    data object ReceiptCaptureStarted : ScannerIntent

    /**
     * QR обнаружен в кадре — сканер уже сам сфотографировал тот же чек и прогнал OCR
     * (см. [ru.vogu.smartfinance.presentation.scanner.ScannerScreen]), поэтому сюда всегда
     * приходят оба результата разом: [qrRawValue] и, если распознались, [ocrLines] с позициями
     * ("Разбивка по позициям в чеке" — CLAUDE.md §4). [ocrLines] может быть пустым — тогда
     * [ru.vogu.smartfinance.domain.usecase.ParseReceiptUseCase.fromQrAndOcr] сам откатится
     * к обычной операции на сумму чека без разбивки.
     */
    data class ReceiptScanned(val qrRawValue: String, val ocrLines: List<String>) : ScannerIntent
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
