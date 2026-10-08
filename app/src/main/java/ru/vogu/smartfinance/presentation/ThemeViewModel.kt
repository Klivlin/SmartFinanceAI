package ru.vogu.smartfinance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.vogu.smartfinance.domain.repository.ThemeRepository
import javax.inject.Inject

/**
 * Держит выбор светлой/тёмной темы (переключатель в шапке [ru.vogu.smartfinance.presentation.chat.ChatScreen]).
 * Создаётся один раз на уровне [MainActivity], поэтому переключение мгновенно применяется
 * ко всему дереву [ru.vogu.smartfinance.presentation.navigation.SmartFinanceNavGraph].
 *
 * `null` — тема ещё не переопределена явно, приложение следует системной; `true`/`false` —
 * пользователь явно закрепил тёмную/светлую тему (переживает перезапуск — CLAUDE.md §5.2).
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themeRepository: ThemeRepository
) : ViewModel() {

    val darkThemeOverride: StateFlow<Boolean?> = themeRepository.isDarkTheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** [currentEffectiveDark] — то, что реально отрисовано прямо сейчас (override ?: системная тема). */
    fun toggleTheme(currentEffectiveDark: Boolean) {
        viewModelScope.launch {
            themeRepository.setDarkTheme(!currentEffectiveDark)
        }
    }
}
