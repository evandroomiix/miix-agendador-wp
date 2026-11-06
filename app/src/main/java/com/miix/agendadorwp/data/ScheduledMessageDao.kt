package com.miix.agendadorwp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledMessageDao {
    @Query("SELECT * FROM scheduled_messages ORDER BY scheduledTimeMillis ASC")
    fun getAllMessagesFlow(): Flow<List<ScheduledMessage>>

    @Query("SELECT * FROM scheduled_messages WHERE status = 'PENDING' ORDER BY scheduledTimeMillis ASC")
    fun getPendingMessagesFlow(): Flow<List<ScheduledMessage>>

    @Query("SELECT * FROM scheduled_messages WHERE id = :id")
    suspend fun getMessageById(id: Long): ScheduledMessage?

    @Query("SELECT * FROM scheduled_messages WHERE status = 'PENDING' AND scheduledTimeMillis <= :now")
    suspend fun getDueMessages(now: Long): List<ScheduledMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ScheduledMessage): Long

    @Update
    suspend fun updateMessage(message: ScheduledMessage)

    @Delete
    suspend fun deleteMessage(message: ScheduledMessage)

    @Query("UPDATE scheduled_messages SET status = :status, lastExecutionLog = :log WHERE id = :id")
    suspend fun updateStatus(id: Long, status: MessageStatus, log: String)
}
