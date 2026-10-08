package ru.vogu.smartfinance.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.vogu.smartfinance.R
import ru.vogu.smartfinance.presentation.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ежедневное напоминание "внести расходы" — CLAUDE.md §2.4/§5.1: если пользователь весь день
 * ничего не записал в чат, мягкое напоминание вечером (см. [ru.vogu.smartfinance.core.work.ExpenseReminderWorker]).
 */
@Singleton
class ExpenseReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun notifyForgottenExpenses() {
        ensureChannel()

        // API 33+: без разрешения POST_NOTIFICATIONS system отклонит notify() тихо —
        // явная проверка вместо падения/пропуска без объяснения.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle("Не забудьте внести расходы")
            .setContentText("Сегодня в SmartFinance AI ещё нет ни одной операции — самое время это исправить.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Сегодня в SmartFinance AI ещё нет ни одной операции. Пара секунд в чате — " +
                        "и вечерний баланс снова точный."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Напоминания о расходах",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Ежедневное напоминание внести операции за день, если их ещё не было."
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "expense_reminders"
        const val NOTIFICATION_ID = 1001
    }
}
