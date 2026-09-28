package ru.vogu.smartfinance.presentation.accounts

import ru.vogu.smartfinance.core.common.Constants
import ru.vogu.smartfinance.core.database.entity.AccountType
import ru.vogu.smartfinance.domain.model.Account
import java.math.BigDecimal

/**
 * MVI-контракт экрана счетов — CLAUDE.md §2.1/§5.2/§6: список счетов с балансами, создание
 * нового счёта и перевод между счетами. Бизнес-логика (валидация, атомарное обновление
 * баланса(ов)) — в [ru.vogu.smartfinance.domain.usecase.ManageAccountUseCase].
 */
sealed interface AccountsIntent {
    data object AddAccountRequested : AccountsIntent
    data object DismissAddAccount : AccountsIntent
    data class CreateAccount(val form: NewAccountForm) : AccountsIntent
    data class TransferRequested(val fromAccountId: Long) : AccountsIntent
    data object DismissTransfer : AccountsIntent
    data class SubmitTransfer(val toAccountId: Long, val amountText: String, val feeText: String) : AccountsIntent
    data class ArchiveAccount(val accountId: Long) : AccountsIntent
    data object ErrorMessageShown : AccountsIntent
}

/** Черновик формы "Новый счёт" — суммы как текст, парсинг/валидация в ViewModel/UseCase. */
data class NewAccountForm(
    val name: String,
    val type: AccountType,
    val currency: String,
    val initialBalanceText: String,
    val creditLimitText: String = "",
    val gracePeriodDaysText: String = "",
    val statementDayText: String = ""
)

data class AccountsState(
    val accounts: List<Account> = emptyList(),
    val baseCurrency: String = Constants.DEFAULT_BASE_CURRENCY,
    val isAddAccountVisible: Boolean = false,
    val transferFromAccountId: Long? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val isLoading: Boolean = true
)

sealed interface AccountsEffect {
    data class ShowToast(val message: String) : AccountsEffect
}
