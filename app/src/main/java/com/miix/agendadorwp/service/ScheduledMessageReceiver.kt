package com.miix.agendadorwp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.miix.agendadorwp.data.AppDatabase
import com.miix.agendadorwp.scheduler.SchedulerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduledMessageReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CONFIRM_SEND = "com.miix.agendadorwp.ACTION_CONFIRM_SEND"
        const val ACTION_SNOOZE_SEND = "com.miix.agendadorwp.ACTION_SNOOZE_SEND"
        private const val ASK_CHANNEL_ID = "miix_ask_before_send_channel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("ScheduledReceiver", "Recebida ação: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                CoroutineScope(Dispatchers.IO).launch {
                    SchedulerManager.rescheduleAllPending(context)
                }
            }
            ACTION_CONFIRM_SEND -> {
                val messageId = intent.getLongExtra(SchedulerManager.EXTRA_MESSAGE_ID, -1L)
                dismissNotification(context, messageId.toInt())
                if (messageId != -1L) {
                    ExecutionForegroundService.startExecution(context, messageId)
                }
            }
            ACTION_SNOOZE_SEND -> {
                val messageId = intent.getLongExtra(SchedulerManager.EXTRA_MESSAGE_ID, -1L)
                dismissNotification(context, messageId.toInt())
                if (messageId != -1L) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val dao = AppDatabase.getInstance(context).scheduledMessageDao()
                        val msg = dao.getMessageById(messageId)
                        if (msg != null) {
                            val newTime = System.currentTimeMillis() + (10 * 60 * 1000) // +10 minutos
                            val updated = msg.copy(scheduledTimeMillis = newTime)
                            dao.updateMessage(updated)
                            SchedulerManager.scheduleMessage(context, updated)
                        }
                    }
                }
            }
            SchedulerManager.ACTION_SEND_SCHEDULED -> {
                val messageId = intent.getLongExtra(SchedulerManager.EXTRA_MESSAGE_ID, -1L)
                if (messageId != -1L) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val dao = AppDatabase.getInstance(context).scheduledMessageDao()
                        val msg = dao.getMessageById(messageId)
                        if (msg != null) {
                            if (msg.askBeforeSending) {
                                showAskBeforeSendNotification(context, msg.id, msg.title, msg.text)
                            } else {
                                ExecutionForegroundService.startExecution(context, messageId)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showAskBeforeSendNotification(context: Context, messageId: Long, title: String, text: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ASK_CHANNEL_ID,
                "Confirmação de Envio",
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
        }

        // Ação: Enviar Agora
        val sendIntent = Intent(context, ScheduledMessageReceiver::class.java).apply {
            action = ACTION_CONFIRM_SEND
            putExtra(SchedulerManager.EXTRA_MESSAGE_ID, messageId)
        }
        val sendPendingIntent = PendingIntent.getBroadcast(
            context,
            messageId.toInt() * 10 + 1,
            sendIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Ação: Adiar 10 min
        val snoozeIntent = Intent(context, ScheduledMessageReceiver::class.java).apply {
            action = ACTION_SNOOZE_SEND
            putExtra(SchedulerManager.EXTRA_MESSAGE_ID, messageId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            messageId.toInt() * 10 + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, ASK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⏰ Enviar agora: $title?")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_media_play, "Enviar Agora", sendPendingIntent)
            .addAction(android.R.drawable.ic_menu_recent_history, "Adiar 10m", snoozePendingIntent)
            .build()

        nm.notify(messageId.toInt(), notif)
    }

    private fun dismissNotification(context: Context, notificationId: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notificationId)
    }
}
