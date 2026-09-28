package ru.vogu.smartfinance.presentation.chat.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Sticky Bottom Bar — CLAUDE.md §5.1: текстовое поле с авто-подгонкой размера, кнопка
 * микрофона (запуск [android.speech.SpeechRecognizer]) и кнопка камеры (переход к сканеру).
 * Позиционирование над клавиатурой (`imePadding`) выполняет вызывающий [ChatScreen] через Scaffold.
 */
@Composable
fun ChatInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    onCameraClick: () -> Unit,
    isSending: Boolean,
    canSend: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(onClick = onCameraClick, enabled = !isSending) {
                Icon(Icons.Filled.CameraAlt, contentDescription = "Сканировать чек")
            }

            OutlinedTextField(
                value = text,
                onValueChange = onTextChanged,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp, max = 140.dp),
                placeholder = { androidx.compose.material3.Text("Например: такси 450") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 5,
                enabled = !isSending
            )

            IconButton(onClick = onMicClick, enabled = !isSending) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Голосовой ввод",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                Crossfade(targetState = isSending, label = "send-button") { sending ->
                    if (sending) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onSend, enabled = canSend) {
                            Icon(
                                Icons.Filled.Send,
                                contentDescription = "Отправить",
                                tint = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}
