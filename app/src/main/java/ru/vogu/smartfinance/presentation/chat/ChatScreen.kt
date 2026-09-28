package ru.vogu.smartfinance.presentation.chat

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import ru.vogu.smartfinance.presentation.chat.components.ChatInputBar
import ru.vogu.smartfinance.presentation.chat.components.DashboardHeader
import ru.vogu.smartfinance.presentation.chat.components.EditTransactionBottomSheet
import ru.vogu.smartfinance.presentation.chat.components.TransactionActionCard
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Conversational Home Screen — CLAUDE.md §5.1: лента сообщений/Action Card, collapsible
 * dashboard header и sticky bottom bar с вводом текста/голоса/камеры.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToScanner: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val recognized = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (recognized != null) {
            viewModel.onIntent(ChatIntent.VoiceResult(recognized))
        } else {
            viewModel.onIntent(ChatIntent.VoiceRecognitionFailed)
        }
    }

    fun launchSpeechRecognizer() {
        try {
            speechRecognizerLauncher.launch(buildSpeechRecognizerIntent())
        } catch (e: ActivityNotFoundException) {
            // На устройстве нет ни одного сервиса распознавания речи (например, сборка без
            // Google-приложений) — сообщаем пользователю, а не падаем.
            viewModel.onIntent(ChatIntent.VoiceRecognitionFailed)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchSpeechRecognizer()
        } else {
            viewModel.onIntent(ChatIntent.VoiceRecognitionFailed)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ChatEffect.ScrollToBottom -> {
                    coroutineScope.launch {
                        if (state.messages.isNotEmpty()) {
                            listState.animateScrollToItem(state.messages.lastIndex)
                        }
                    }
                }
                ChatEffect.NavigateToScanner -> onNavigateToScanner()
                ChatEffect.RequestVoiceInput -> {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        launchSpeechRecognizer()
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                is ChatEffect.ShowToast -> coroutineScope.launch {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onIntent(ChatIntent.ErrorMessageShown)
    }

    state.editingTransaction?.let { editing ->
        EditTransactionBottomSheet(
            transaction = editing,
            categories = state.editingCategories,
            accounts = state.editingAccounts,
            onDismiss = { viewModel.onIntent(ChatIntent.DismissEdit) },
            onSave = { viewModel.onIntent(ChatIntent.SaveEditedTransaction(it)) }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("SmartFinance AI") })
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            DashboardHeader(
                summary = state.dashboard,
                isExpanded = state.isHeaderExpanded,
                onToggleExpand = { viewModel.onIntent(ChatIntent.HeaderExpandToggled) }
            )

            Box(modifier = Modifier.weight(1f)) {
                if (state.messages.isEmpty()) {
                    EmptyChatPlaceholder(modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.messages, key = { it.stableId }) { message ->
                            ChatMessageRow(
                                message = message,
                                onConfirm = { id -> viewModel.onIntent(ChatIntent.ConfirmTransaction(id)) },
                                onEdit = { id -> viewModel.onIntent(ChatIntent.EditTransactionRequested(id)) },
                                onUndo = { id -> viewModel.onIntent(ChatIntent.UndoTransaction(id)) }
                            )
                        }
                    }
                }
            }

            ChatInputBar(
                text = state.inputText,
                onTextChanged = { viewModel.onIntent(ChatIntent.InputTextChanged(it)) },
                onSend = { viewModel.onIntent(ChatIntent.SendMessage) },
                onMicClick = { viewModel.onIntent(ChatIntent.MicClicked) },
                onCameraClick = { viewModel.onIntent(ChatIntent.CameraClicked) },
                isSending = state.isSending,
                canSend = state.canSend,
                modifier = Modifier.imePadding()
            )
        }
    }
}

@Composable
private fun ChatMessageRow(
    message: ChatMessage,
    onConfirm: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onUndo: (Long) -> Unit
) {
    when (message) {
        is ChatMessage.UserText -> UserMessageBubble(message)
        is ChatMessage.TransactionCard -> TransactionActionCard(
            card = message,
            onConfirm = { onConfirm(message.transaction.id) },
            onEdit = { onEdit(message.transaction.id) },
            onUndo = { onUndo(message.transaction.id) }
        )
    }
}

@Composable
private fun UserMessageBubble(message: ChatMessage.UserText) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (message.isPending) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.padding(start = 48.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(text = message.text, color = MaterialTheme.colorScheme.onPrimary)
                Text(
                    text = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                        .withLocale(Locale("ru", "RU"))
                        .format(message.timestamp.atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                )
            }
        }
    }
}

@Composable
private fun EmptyChatPlaceholder(modifier: Modifier = Modifier) {
    Text(
        text = "Напишите, например: «такси 450» или «кофе 250 с карты» — SmartFinance AI сам разберёт операцию.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 32.dp)
    )
}

private fun buildSpeechRecognizerIntent() =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажите, например: такси 450")
    }
