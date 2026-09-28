package ru.vogu.smartfinance.domain.repository

import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.domain.model.ParsedExpense

/**
 * Абстрагирует 3-уровневый каскад разбора NLI-ввода — CLAUDE.md §3.
 * Скрывает от [ru.vogu.smartfinance.domain.usecase.ProcessNliInputUseCase], какой именно
 * уровень (regex / semantic cache / LLM) дал результат — тот получает единый
 * `List<ParsedExpense>` (LLM может вернуть несколько операций из одной фразы).
 */
interface NliExpenseRepository {
    suspend fun parseUserInput(rawInputText: String): Resource<List<ParsedExpense>>
}
