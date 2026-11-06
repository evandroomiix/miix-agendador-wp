package com.miix.agendadorwp.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.miix.agendadorwp.data.*
import com.miix.agendadorwp.service.ScheduledMessageReceiver
import java.util.Calendar

object SchedulerManager {

    private const val TAG = "SchedulerManager"
    const val ACTION_SEND_SCHEDULED = "com.miix.agendadorwp.ACTION_SEND_SCHEDULED"
    const val EXTRA_MESSAGE_ID = "extra_message_id"

    fun scheduleMessage(context: Context, message: ScheduledMessage) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScheduledMessageReceiver::class.java).apply {
            action = ACTION_SEND_SCHEDULED
            putExtra(EXTRA_MESSAGE_ID, message.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            message.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = message.scheduledTimeMillis
        if (triggerTime <= System.currentTimeMillis()) {
            Log.w(TAG, "Horário agendado já passou para a mensagem ${message.id}")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d(TAG, "Mensagem ${message.id} agendada para: $triggerTime")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permissão SCHEDULE_EXACT_ALARM necessária: ${e.message}")
        }
    }

    fun cancelSchedule(context: Context, messageId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScheduledMessageReceiver::class.java).apply {
            action = ACTION_SEND_SCHEDULED
            putExtra(EXTRA_MESSAGE_ID, messageId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            messageId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Agendamento $messageId cancelado")
    }

    suspend fun rescheduleAllPending(context: Context) {
        val dao = AppDatabase.getInstance(context).scheduledMessageDao()
        val now = System.currentTimeMillis()
        val dueMessages = dao.getDueMessages(now + 365L * 24 * 3600 * 1000)
        for (msg in dueMessages) {
            if (msg.scheduledTimeMillis > now) {
                scheduleMessage(context, msg)
            }
        }
    }

    suspend fun rescheduleRepeat(context: Context, message: ScheduledMessage) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = message.scheduledTimeMillis
        }

        when (message.repeatMode) {
            RepeatMode.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            RepeatMode.WEEKDAYS -> {
                do {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                } while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
            }
            RepeatMode.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            RepeatMode.MONTHLY -> cal.add(Calendar.MONTH, 1)
            RepeatMode.NONE -> return
        }

        val updated = message.copy(
            scheduledTimeMillis = cal.timeInMillis,
            status = MessageStatus.PENDING,
            lastExecutionLog = "Reagendado automaticamente para ciclo repetitivo."
        )

        val dao = AppDatabase.getInstance(context).scheduledMessageDao()
        dao.updateMessage(updated)
        scheduleMessage(context, updated)
    }
}
