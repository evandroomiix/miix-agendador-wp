package com.miix.agendadorwp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class WhatsAppType {
    WHATSAPP_NORMAL,
    WHATSAPP_BUSINESS,
    WHATSAPP_MOD,
    INSTAGRAM,
    INSTAGRAM_MOD,
    FACEBOOK,
    META_BUSINESS
}

enum class ScheduleType {
    CONTACT_GROUP,       // Contatos e Grupos normais
    STATUS,              // Postar no Status / Stories
    POLL,                // Enquetes interativas do WhatsApp
    BROADCAST_LIST,      // Listas de Transmissão
    ENGAGEMENT_CAMPAIGN  // Campanhas de engajamento em massa com variáveis
}

enum class AttachmentType {
    NONE,
    IMAGE_VIDEO,
    DOCUMENT,
    AUDIO,
    VOICE_NOTE
}

enum class MessageStatus {
    PENDING,    // Pendentes
    DONE,       // Feito
    FAILED,     // Falhado
    DRAFT,      // Rascunhos
    DELETED,    // Excluído (Lixeira)
    PAUSED      // Pausado
}

enum class RepeatMode {
    NONE,
    DAILY,
    WEEKDAYS,
    WEEKLY,
    MONTHLY
}

@Entity(tableName = "scheduled_messages")
data class ScheduledMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val text: String,
    val recipientsJson: String, // Lista de destinatários em JSON [{"name":"João","phone":"5511999999999"}]
    val scheduledTimeMillis: Long,
    val whatsAppType: WhatsAppType = WhatsAppType.WHATSAPP_NORMAL,
    val scheduleType: ScheduleType = ScheduleType.CONTACT_GROUP,
    val attachmentType: AttachmentType = AttachmentType.NONE,
    val attachmentUri: String? = null,
    val attachmentName: String? = null,
    val pollQuestion: String = "",
    val pollOptionsJson: String = "[]", // ["Opção 1", "Opção 2", ...]
    val askBeforeSending: Boolean = false, // Perguntar-me antes de enviar
    val checkTimeConflict: Boolean = true, // Verificar conflito de tempo
    val calendarSync: Boolean = false,     // Sincronizar com calendário
    val calendarEventId: Long? = null,
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val delayBetweenContactsSeconds: Int = 10, // Intervalo anti-ban entre contatos
    val status: MessageStatus = MessageStatus.PENDING,
    val lastExecutionLog: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
