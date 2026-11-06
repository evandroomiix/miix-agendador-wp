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

            dao.updateStatus(message.id, MessageStatus.PENDING, "Processando fila de envio...")

            try {
                val recipients = parseRecipients(message.recipientsJson)
                val total = recipients.size.coerceAtLeast(1)
                var successCount = 0

                val pkgTarget = resolvePackageTarget(message.whatsAppType)

                // Caso seja postagem de STATUS / STORIES
                if (message.scheduleType == ScheduleType.STATUS) {
                    updateNotification("Postando no Status do WhatsApp...")
                    val posted = postToStatus(message, pkgTarget)
                    if (posted) successCount++
                } else {
                    for (i in recipients.indices) {
                        val contact = recipients[i]
                        val phone = contact.first
                        val name = contact.second

                        updateNotification("Enviando ${i + 1} de $total para $name...")

                        // Personalizar variáveis da mensagem
                        var textPersonalizado = message.text
                            .replace("{nome}", name)
                            .replace("{name}", name)
                            .replace("{telefone}", phone)
                            .replace("{saudacao}", getSaudacaoAtual())

                        // Se for enquete, formata o texto com as opções
                        if (message.scheduleType == ScheduleType.POLL && message.pollQuestion.isNotBlank()) {
                            textPersonalizado = formatPollText(message.pollQuestion, message.pollOptionsJson) + 
                                (if (textPersonalizado.isNotBlank()) "\n\n$textPersonalizado" else "")
                        }

                        val sent = sendSingleMessage(phone, textPersonalizado, pkgTarget, message.attachmentUri, message.attachmentType)
                        if (sent) {
                            successCount++
                        }

                        // Intervalo anti-ban entre contatos
                        if (i < recipients.size - 1) {
                            val delaySecs = message.delayBetweenContactsSeconds.coerceAtLeast(3)
                            delay(delaySecs * 1000L)
                        }
                    }
                }

                // Finalização
                val logFinal = "Sucesso: $successCount de $total envios concluídos com êxito."
                dao.updateStatus(message.id, MessageStatus.DONE, logFinal)

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

    private fun resolvePackageTarget(type: WhatsAppType): String {
        return when (type) {
            WhatsAppType.WHATSAPP_NORMAL -> "com.whatsapp"
            WhatsAppType.WHATSAPP_BUSINESS -> "com.whatsapp.w4b"
            WhatsAppType.WHATSAPP_MOD -> PlatformHelper.getInstalledWhatsAppModPackage(this)
            WhatsAppType.INSTAGRAM -> "com.instagram.android"
            WhatsAppType.INSTAGRAM_MOD -> PlatformHelper.getInstalledInstagramPackage(this)
            WhatsAppType.FACEBOOK -> {
                val faces = listOf("com.facebook.katana", "com.facebook.lite")
                faces.firstOrNull { PlatformHelper.isAppInstalled(this, it) } ?: "com.facebook.katana"
            }
            WhatsAppType.META_BUSINESS -> "com.facebook.pages.app"
        }
    }

    private suspend fun sendSingleMessage(
        phone: String,
        text: String,
        pkgTarget: String,
        attachmentUriStr: String?,
        attachmentType: AttachmentType
    ): Boolean {
        return withContext(Dispatchers.Main) {
            val completer = CompletableDeferred<Boolean>()

            WhatsAppAccessibilityService.isAutomating = true
            WhatsAppAccessibilityService.onMessageSentListener = {
                if (!completer.isCompleted) {
                    completer.complete(true)
                }
            }

            val cleanPhone = phone.replace(Regex("[^0-9]"), "")
            
            val intent = if (!attachmentUriStr.isNullOrBlank() && attachmentType != AttachmentType.NONE) {
                // Envio com Anexo (Imagem/Vídeo/Documento/Áudio)
                val fileUri = Uri.parse(attachmentUriStr)
                Intent(Intent.ACTION_SEND).apply {
                    setPackage(pkgTarget)
                    val mime = when (attachmentType) {
                        AttachmentType.IMAGE_VIDEO -> "image/*"
                        AttachmentType.AUDIO, AttachmentType.VOICE_NOTE -> "audio/*"
                        AttachmentType.DOCUMENT -> "application/*"
                        else -> "*/*"
                    }
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    if (text.isNotBlank()) {
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    if (cleanPhone.isNotBlank()) {
                        putExtra("jid", "$cleanPhone@s.whatsapp.net")
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            } else {
                // Envio de Texto via Deep Link Oficial
                val encodedText = URLEncoder.encode(text, "UTF-8")
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedText")
                Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(pkgTarget)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }

            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Não foi possível abrir o app alvo ($pkgTarget): ${e.message}")
                WhatsAppAccessibilityService.isAutomating = false
                return@withContext false
            }

            // Aguarda até 12 segundos pela confirmação do clique pelo serviço de acessibilidade
            try {
                withTimeout(12000L) {
                    completer.await()
                }
            } catch (te: TimeoutCancellationException) {
                Log.w(TAG, "Timeout aguardando envio para $phone")
                WhatsAppAccessibilityService.isAutomating = false
                false
            }
        }
    }

    private suspend fun postToStatus(message: ScheduledMessage, pkgTarget: String): Boolean {
        return withContext(Dispatchers.Main) {
            val completer = CompletableDeferred<Boolean>()
            WhatsAppAccessibilityService.isAutomating = true
            WhatsAppAccessibilityService.onMessageSentListener = {
                if (!completer.isCompleted) completer.complete(true)
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                setPackage(pkgTarget)
                if (!message.attachmentUri.isNullOrBlank()) {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(message.attachmentUri))
                } else {
                    type = "text/plain"
                }
                putExtra(Intent.EXTRA_TEXT, message.text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                startActivity(intent)
                withTimeout(15000L) { completer.await() }
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun formatPollText(question: String, optionsJson: String): String {
        val sb = StringBuilder("📊 *$question*\n")
        try {
            val arr = JSONArray(optionsJson)
            val emojis = listOf("1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔟")
            for (i in 0 until arr.length()) {
                val opt = arr.optString(i, "")
                if (opt.isNotBlank()) {
                    val emoji = if (i < emojis.size) emojis[i] else "🔘"
                    sb.append("\n$emoji $opt")
                }
            }
        } catch (e: Exception) {
            sb.append("\n$optionsJson")
        }
        return sb.toString()
    }

    private fun getSaudacaoAtual(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Bom dia"
            in 12..17 -> "Boa tarde"
            else -> "Boa noite"
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
            wakeLock?.acquire(10 * 60 * 1000L)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao obter WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
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
