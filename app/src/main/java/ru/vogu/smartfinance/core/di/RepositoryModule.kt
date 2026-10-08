package ru.vogu.smartfinance.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.vogu.smartfinance.data.repository.AccountRepositoryImpl
import ru.vogu.smartfinance.data.repository.CategoryRepositoryImpl
import ru.vogu.smartfinance.data.repository.ChatHistoryRepositoryImpl
import ru.vogu.smartfinance.data.repository.ExchangeRateRepositoryImpl
import ru.vogu.smartfinance.data.repository.NliExpenseRepositoryImpl
import ru.vogu.smartfinance.data.repository.OnboardingRepositoryImpl
import ru.vogu.smartfinance.data.repository.ReceiptRepositoryImpl
import ru.vogu.smartfinance.data.repository.ReminderSettingsRepositoryImpl
import ru.vogu.smartfinance.data.repository.SemanticAliasRepositoryImpl
import ru.vogu.smartfinance.data.repository.ThemeRepositoryImpl
import ru.vogu.smartfinance.data.repository.TransactionRepositoryImpl
import ru.vogu.smartfinance.domain.repository.AccountRepository
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import ru.vogu.smartfinance.domain.repository.ChatHistoryRepository
import ru.vogu.smartfinance.domain.repository.ExchangeRateRepository
import ru.vogu.smartfinance.domain.repository.NliExpenseRepository
import ru.vogu.smartfinance.domain.repository.OnboardingRepository
import ru.vogu.smartfinance.domain.repository.ReceiptRepository
import ru.vogu.smartfinance.domain.repository.ReminderSettingsRepository
import ru.vogu.smartfinance.domain.repository.SemanticAliasRepository
import ru.vogu.smartfinance.domain.repository.ThemeRepository
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindSemanticAliasRepository(impl: SemanticAliasRepositoryImpl): SemanticAliasRepository

    @Binds
    @Singleton
    abstract fun bindNliExpenseRepository(impl: NliExpenseRepositoryImpl): NliExpenseRepository

    @Binds
    @Singleton
    abstract fun bindReceiptRepository(impl: ReceiptRepositoryImpl): ReceiptRepository

    @Binds
    @Singleton
    abstract fun bindThemeRepository(impl: ThemeRepositoryImpl): ThemeRepository

    @Binds
    @Singleton
    abstract fun bindChatHistoryRepository(impl: ChatHistoryRepositoryImpl): ChatHistoryRepository

    @Binds
    @Singleton
    abstract fun bindReminderSettingsRepository(impl: ReminderSettingsRepositoryImpl): ReminderSettingsRepository

    @Binds
    @Singleton
    abstract fun bindOnboardingRepository(impl: OnboardingRepositoryImpl): OnboardingRepository

    @Binds
    @Singleton
    abstract fun bindExchangeRateRepository(impl: ExchangeRateRepositoryImpl): ExchangeRateRepository
}
