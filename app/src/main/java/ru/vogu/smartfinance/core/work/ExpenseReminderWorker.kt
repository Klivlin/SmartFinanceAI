package ru.vogu.smartfinance.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ru.vogu.smartfinance.core.common.Constants
import ru.vogu.smartfinance.core.common.startOfDay
import ru.vogu.smartfinance.core.notification.ExpenseReminderNotifier
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Ежедневное напоминание "внести расходы" — CLAUDE.md §2.4/§5.1. Раз в сутки (ближе к вечеру,
 * см. [scheduleDaily]) проверяет, была ли хоть одна операция сегодня; если нет — показывает
 * уведомление. Ничего не показывает, если пользователь уже вносил операции за день —
 * напоминание не должно раздражать тех, кто и так ведёт учёт.
 */
@HiltWorker
class ExpenseReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transactionRepository: TransactionRepository,
    private val notifier: ExpenseReminderNotifier
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = Instant.now()
        val startOfToday = now.startOfDay()
        val count = transactionRepository.countByDateRange(startOfToday, now)
        if (count == 0) {
            notifier.notifyForgottenExpenses()
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = Constants.WORK_TAG_EXPENSE_REMINDER
        private val REMINDER_HOUR = 20 // 20:00 по местному времени — типично конец рабочего дня

        /**
         * Планирует ежедневный запуск. [ExistingPeriodicWorkPolicy.KEEP] — повторный вызов
         * (например, при каждом старте Activity) не пересоздаёт уже запланированную работу.
         */
        fun scheduleDaily(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<ExpenseReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(computeInitialDelay(), TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun computeInitialDelay(): Long {
            val zone = ZoneId.systemDefault()
            val now = java.time.ZonedDateTime.now(zone)
            val todayAtReminderHour = LocalDate.now(zone).atTime(LocalTime.of(REMINDER_HOUR, 0)).atZone(zone)
            val nextRun = if (now.isBefore(todayAtReminderHour)) todayAtReminderHour else todayAtReminderHour.plusDays(1)
            return Duration.between(now, nextRun).toMillis().coerceAtLeast(0L)
        }
    }
}
