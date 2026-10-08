package ru.vogu.smartfinance.presentation.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.domain.usecase.ParseReceiptUseCase
import javax.inject.Inject

private const val DEFAULT_STATUS_HINT = "Наведите камеру на QR-код чека или сфотографируйте его целиком"

/**
 * ViewModel сканера — CLAUDE.md §6. Не содержит камеры/ML Kit: получает уже готовые данные
 * (сырое значение штрихкода или распознанные строки текста) через [ScannerIntent] и делегирует
 * всю бизнес-логику [ParseReceiptUseCase].
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val parseReceiptUseCase: ParseReceiptUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ScannerState())
    val state: StateFlow<ScannerState> = _state.asStateFlow()

    private val _effect = Channel<ScannerEffect>(Channel.BUFFERED)
    val effect: Flow<ScannerEffect> = _effect.receiveAsFlow()

    /**
     * Последнее значение QR, для которого весь путь (QR + OCR) закончился ошибкой. Разбор QR
     * полностью локальный и детерминированный (см. [ru.vogu.smartfinance.data.parser.FiscalQrParser]),
     * а OCR/Gemini для того же кадра с высокой вероятностью повторит тот же результат — не
     * спамим пользователя одинаковыми сообщениями и повторными сетевыми вызовами, пока в кадре
     * остаётся один и тот же QR.
     */
    private var lastFailedQrValue: String? = null

    fun onIntent(intent: ScannerIntent) {
        when (intent) {
            ScannerIntent.ReceiptCaptureStarted -> _state.update {
                it.copy(isProcessing = true, statusHint = "Обрабатываем чек…")
            }
            is ScannerIntent.ReceiptScanned -> onReceiptScanned(intent.qrRawValue, intent.ocrLines)
            is ScannerIntent.PhotoRecognized -> onPhotoRecognized(intent.lines)
            ScannerIntent.PhotoCaptureFailed -> _state.update {
                it.copy(isProcessing = false, errorMessage = "Не удалось сделать фото. Попробуйте ещё раз.")
            }
            ScannerIntent.CameraPermissionDenied -> _state.update {
                it.copy(errorMessage = "Без доступа к камере сканер недоступен. Разрешите доступ в настройках приложения.")
            }
            ScannerIntent.ErrorMessageShown -> _state.update { it.copy(errorMessage = null) }
            ScannerIntent.CloseRequested -> viewModelScope.launch { _effect.send(ScannerEffect.NavigateBack) }
        }
    }

    private fun onReceiptScanned(qrRawValue: String, ocrLines: List<String>) {
        // isProcessing к этому моменту уже true (см. [ScannerIntent.ReceiptCaptureStarted],
        // отправляется экраном раньше — сразу по факту находки QR, до фото+OCR), поэтому здесь
        // проверяем только повторную попытку того же самого QR, из-за которого уже была ошибка.
        if (qrRawValue == lastFailedQrValue) return

        viewModelScope.launch {
            when (val result = parseReceiptUseCase.fromQrAndOcr(qrRawValue, ocrLines)) {
                is Resource.Success -> _effect.send(ScannerEffect.NavigateBack)
                is Resource.Error -> {
                    lastFailedQrValue = qrRawValue
                    _state.update {
                        it.copy(isProcessing = false, errorMessage = result.message, statusHint = DEFAULT_STATUS_HINT)
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }

    private fun onPhotoRecognized(lines: List<String>) {
        if (_state.value.isProcessing) return

        _state.update { it.copy(isProcessing = true, statusHint = "Распознаём позиции чека (Gemini)…") }
        viewModelScope.launch {
            when (val result = parseReceiptUseCase.fromOcrLines(lines)) {
                is Resource.Success -> _effect.send(ScannerEffect.NavigateBack)
                is Resource.Error -> _state.update {
                    it.copy(isProcessing = false, errorMessage = result.message, statusHint = DEFAULT_STATUS_HINT)
                }
                Resource.Loading -> Unit
            }
        }
    }
}
