package com.miix.agendadorwp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledMessageDao {
    @Query("SELECT * FROM scheduled_messages ORDER BY scheduledTimeMillis ASC")
    fun getAllMessagesFlow(): Flow<List<ScheduledMessage>>

    @Query("SELECT * FROM scheduled_messages WHERE status = 'PENDING' ORDER BY scheduledTimeMillis ASC")
    fun getPendingMessagesFlow(): Flow<List<ScheduledMessage>>

    @Query("SELECT * FROM scheduled_messages WHERE status = 'DONE' ORDER BY scheduledTimeMillis DESC")
    fun getDoneMessagesFlow(): Flow<List<ScheduledMessage>>

    @Query("SELECT * FROM scheduled_messages WHERE status = 'FAILED' ORDER BY scheduledTimeMillis DESC")
    fun getFailedMessagesFlow(): Flow<List<ScheduledMessage>>

    @Query("SELECT * FROM scheduled_messages WHERE status = 'DRAFT' ORDER BY createdAt DESC")
    fun getDraftMessagesFlow(): Flow<List<ScheduledMessage>>

    @Query("SELECT * FROM scheduled_messages WHERE status = 'DELETED' ORDER BY scheduledTimeMillis DESC")
    fun getDeletedMessagesFlow(): Flow<List<ScheduledMessage>>

    @Query("SELECT * FROM scheduled_messages WHERE id = :id")
    suspend fun getMessageById(id: Long): ScheduledMessage?

    @Query("SELECT * FROM scheduled_messages WHERE status = 'PENDING' AND scheduledTimeMillis <= :now")
    suspend fun getDueMessages(now: Long): List<ScheduledMessage>

    // Verificar se existe conflito de tempo (outra mensagem agendada no intervalo de tempo de +/- minutos)
    @Query("SELECT * FROM scheduled_messages WHERE status = 'PENDING' AND scheduledTimeMillis BETWEEN :startTime AND :endTime AND id != :excludeId")
    suspend fun findTimeConflicts(startTime: Long, endTime: Long, excludeId: Long = -1): List<ScheduledMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ScheduledMessage): Long

    @Update
    suspend fun updateMessage(message: ScheduledMessage)

    @Delete
    suspend fun deleteMessage(message: ScheduledMessage)

    // Soft delete (mover para Excluídos / Lixeira)
    @Query("UPDATE scheduled_messages SET status = 'DELETED' WHERE id = :id")
    suspend fun softDelete(id: Long)

    // Restaurar da lixeira para Pendente
    @Query("UPDATE scheduled_messages SET status = 'PENDING' WHERE id = :id")
    suspend fun restoreMessage(id: Long)

    // Esvaziar lixeira permanentemente
    @Query("DELETE FROM scheduled_messages WHERE status = 'DELETED'")
    suspend fun emptyTrash()

    @Query("UPDATE scheduled_messages SET status = :status, lastExecutionLog = :log WHERE id = :id")
    suspend fun updateStatus(id: Long, status: MessageStatus, log: String)
}
