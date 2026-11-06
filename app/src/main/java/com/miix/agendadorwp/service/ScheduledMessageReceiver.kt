package com.miix.agendadorwp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.miix.agendadorwp.data.AppDatabase
import com.miix.agendadorwp.scheduler.SchedulerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduledMessageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("ScheduledReceiver", "Recebida ação: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED) {
            // Reagendar todas as mensagens pendentes após reinicialização do celular
            CoroutineScope(Dispatchers.IO).launch {
                SchedulerManager.rescheduleAllPending(context)
            }
        } else if (action == SchedulerManager.ACTION_SEND_SCHEDULED) {
            val messageId = intent.getLongExtra(SchedulerManager.EXTRA_MESSAGE_ID, -1L)
            if (messageId != -1L) {
                ExecutionForegroundService.startExecution(context, messageId)
            }
        }
    }
}
