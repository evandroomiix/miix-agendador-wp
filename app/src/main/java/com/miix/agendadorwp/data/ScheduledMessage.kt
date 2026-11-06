package com.miix.agendadorwp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class WhatsAppType {
    WHATSAPP_NORMAL,
    WHATSAPP_BUSINESS
}

enum class MessageStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED,
    CANCELLED
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
    val recipientsJson: String, // Lista de telefones/nomes em JSON
    val scheduledTimeMillis: Long,
    val whatsAppType: WhatsAppType = WhatsAppType.WHATSAPP_NORMAL,
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val delayBetweenContactsSeconds: Int = 10, // Intervalo anti-ban entre contatos
    val status: MessageStatus = MessageStatus.PENDING,
    val lastExecutionLog: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
