package com.miix.agendadorwp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.miix.agendadorwp.data.*
import com.miix.agendadorwp.scheduler.SchedulerManager
import kotlinx.coroutines.*
import org.json.JSONArray
import java.net.URLEncoder

class ExecutionForegroundService : Service() {

    companion object {
        private const val TAG = "ExecutionService"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        private const val CHANNEL_ID = "miix_execution_channel"
        private const val NOTIF_ID = 1001

        fun startExecution(context: Context, messageId: Long) {
            val intent = Intent(context, ExecutionForegroundService::class.java).apply {
                putExtra(EXTRA_MESSAGE_ID, messageId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val messageId = intent?.getLongExtra(EXTRA_MESSAGE_ID, -1L) ?: -1L
        if (messageId != -1L) {
            startForeground(NOTIF_ID, buildNotification("Iniciando envio agendado..."))
            processScheduledMessage(messageId)
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun processScheduledMessage(messageId: Long) {
        serviceScope.launch {
            val dao = AppDatabase.getInstance(applicationContext).scheduledMessageDao()
            val message = dao.getMessageById(messageId)

            if (message == null) {
                stopSelf()
                return@launch
            }

            dao.updateStatus(message.id, MessageStatus.SENDING, "Processando fila de contatos...")

            try {
                val recipients = parseRecipients(message.recipientsJson)
                val total = recipients.size
                var successCount = 0

                val pkgTarget = if (message.whatsAppType == WhatsAppType.WHATSAPP_BUSINESS) {
                    "com.whatsapp.w4b"
                } else {
                    "com.whatsapp"
                }

                for (i in 0 until total) {
                    val contact = recipients[i]
                    val phone = contact.first
                    val name = contact.second

                    updateNotification("Enviando ${i + 1} de $total para $name...")

                    // Personalizar variáveis da mensagem
                    val textPersonalizado = message.text
                        .replace("{nome}", name)
                        .replace("{name}", name)
                        .replace("{telefone}", phone)

                    val sent = sendSingleMessage(phone, textPersonalizado, pkgTarget)
                    if (sent) {
                        successCount++
                    }

                    // Se ainda houver contatos na fila, aguarda o intervalo anti-ban
                    if (i < total - 1) {
                        val delaySecs = message.delayBetweenContactsSeconds.coerceAtLeast(3)
                        delay(delaySecs * 1000L)
                    }
                }

                // Finalização
                val logFinal = "Enviados: $successCount de $total com sucesso."
                dao.updateStatus(message.id, MessageStatus.SENT, logFinal)

                // Repetição se configurada
                if (message.repeatMode != RepeatMode.NONE) {
                    SchedulerManager.rescheduleRepeat(applicationContext, message)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro na execução da fila: ${e.message}", e)
                dao.updateStatus(message.id, MessageStatus.FAILED, "Erro: ${e.message}")
            } finally {
                WhatsAppAccessibilityService.isAutomating = false
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun sendSingleMessage(phone: String, text: String, pkgTarget: String): Boolean {
        return withContext(Dispatchers.Main) {
            val completer = CompletableDeferred<Boolean>()

            WhatsAppAccessibilityService.isAutomating = true
            WhatsAppAccessibilityService.onMessageSentListener = {
                if (!completer.isCompleted) {
                    completer.complete(true)
                }
            }

            // Normaliza telefone (apenas números)
            val cleanPhone = phone.replace(Regex("[^0-9]"), "")
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedText")

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(pkgTarget)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Não foi possível abrir o WhatsApp ($pkgTarget): ${e.message}")
                WhatsAppAccessibilityService.isAutomating = false
                return@withContext false
            }

            // Aguarda até 10 segundos pela confirmação do clique pelo serviço de acessibilidade
            try {
                withTimeout(10000L) {
                    completer.await()
                }
            } catch (te: TimeoutCancellationException) {
                Log.w(TAG, "Timeout aguardando envio do WhatsApp para $phone")
                WhatsAppAccessibilityService.isAutomating = false
                false
            }
        }
    }

    private fun parseRecipients(json: String): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val phone = obj.optString("phone", "")
                val name = obj.optString("name", phone)
                if (phone.isNotBlank()) {
                    list.add(Pair(phone, name))
                }
            }
        } catch (e: Exception) {
            // Formato simples de fallback caso seja apenas texto separado por vírgula
            json.split(",").forEach {
                val p = it.trim()
                if (p.isNotBlank()) list.add(Pair(p, p))
            }
        }
        return list
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "MiixAgendador:ExecutionWakeLock"
            )
            wakeLock?.acquire(10 * 60 * 1000L) // 10 minutos máximo
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao obter WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao liberar WakeLock: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Envios em Andamento",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação ativa durante o envio automatizado de mensagens"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Miix Agendador WP")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        releaseWakeLock()
    }
}
