package ru.vogu.smartfinance.presentation.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.domain.repository.AccountRepository
import ru.vogu.smartfinance.domain.usecase.ManageAccountUseCase
import javax.inject.Inject

/**
 * ViewModel экрана счетов — CLAUDE.md §5.2/§6. Не содержит бизнес-логики: список счетов —
 * из [AccountRepository.observeActive] (реактивно; архивные счета CLAUDE.md §2.1 скрыты из
 * выбора, но их история сохраняется — см. FK RESTRICT в Entities.kt), создание/перевод/
 * архивация — через [ManageAccountUseCase]; здесь только парсинг черновика формы.
 */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val manageAccountUseCase: ManageAccountUseCase
) : ViewModel() {

    private val uiState = MutableStateFlow(AccountsState())

    private val _effect = Channel<AccountsEffect>(Channel.BUFFERED)
    val effect: Flow<AccountsEffect> = _effect.receiveAsFlow()

    val state: StateFlow<AccountsState> = combine(uiState, accountRepository.observeActive()) { base, accounts ->
        base.copy(accounts = accounts.sortedBy { it.sortOrder }, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsState())

    fun onIntent(intent: AccountsIntent) {
        when (intent) {
            AccountsIntent.AddAccountRequested -> uiState.update { it.copy(isAddAccountVisible = true) }
            AccountsIntent.DismissAddAccount -> uiState.update { it.copy(isAddAccountVisible = false) }
            is AccountsIntent.CreateAccount -> createAccount(intent.form)
            is AccountsIntent.TransferRequested -> uiState.update { it.copy(transferFromAccountId = intent.fromAccountId) }
            AccountsIntent.DismissTransfer -> uiState.update { it.copy(transferFromAccountId = null) }
            is AccountsIntent.SubmitTransfer -> submitTransfer(intent)
            is AccountsIntent.ArchiveAccount -> archiveAccount(intent.accountId)
            AccountsIntent.ErrorMessageShown -> uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun createAccount(form: NewAccountForm) {
        val initialBalance = form.initialBalanceText.trim().replace(',', '.').ifBlank { "0" }.toBigDecimalOrNull()
        if (initialBalance == null) {
            uiState.update { it.copy(errorMessage = "Введите корректный начальный баланс.") }
            return
        }
        val creditLimit = form.creditLimitText.trim().replace(',', '.').toBigDecimalOrNull()
        val gracePeriodDays = form.gracePeriodDaysText.trim().toIntOrNull()
        val statementDay = form.statementDayText.trim().toIntOrNull()

        uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = manageAccountUseCase.createAccount(
                name = form.name,
                type = form.type,
                currency = form.currency,
                initialBalance = initialBalance,
                creditLimit = creditLimit,
                gracePeriodDays = gracePeriodDays,
                statementDay = statementDay
            )
            when (result) {
                is Resource.Success -> {
                    uiState.update { it.copy(isSubmitting = false, isAddAccountVisible = false) }
                    _effect.send(AccountsEffect.ShowToast("Счёт «${result.data.name}» создан"))
                }
                is Resource.Error -> uiState.update { it.copy(isSubmitting = false, errorMessage = result.message) }
                Resource.Loading -> Unit
            }
        }
    }

    private fun submitTransfer(intent: AccountsIntent.SubmitTransfer) {
        val fromAccountId = uiState.value.transferFromAccountId ?: return
        val amount = intent.amountText.trim().replace(',', '.').toBigDecimalOrNull()
        if (amount == null) {
            uiState.update { it.copy(errorMessage = "Введите корректную сумму перевода.") }
            return
        }
        val fee = intent.feeText.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()
        if (intent.feeText.isNotBlank() && fee == null) {
            uiState.update { it.copy(errorMessage = "Введите корректную сумму комиссии.") }
            return
        }

        uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = manageAccountUseCase.transfer(
                fromAccountId = fromAccountId,
                toAccountId = intent.toAccountId,
                amount = amount,
                feeAmount = fee
            )
            when (result) {
                is Resource.Success -> {
                    uiState.update { it.copy(isSubmitting = false, transferFromAccountId = null) }
                    _effect.send(AccountsEffect.ShowToast("Перевод выполнен"))
                }
                is Resource.Error -> uiState.update { it.copy(isSubmitting = false, errorMessage = result.message) }
                Resource.Loading -> Unit
            }
        }
    }

    private fun archiveAccount(accountId: Long) {
        viewModelScope.launch {
            when (val result = manageAccountUseCase.archiveAccount(accountId)) {
                is Resource.Error -> uiState.update { it.copy(errorMessage = result.message) }
                is Resource.Success -> _effect.send(AccountsEffect.ShowToast("Счёт архивирован"))
                Resource.Loading -> Unit
            }
        }
    }
}
