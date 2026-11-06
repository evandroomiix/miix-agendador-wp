package com.miix.agendadorwp.service

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.TimeZone

object CalendarSyncHelper {

    fun hasCalendarPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    fun syncEvent(
        context: Context,
        title: String,
        description: String,
        startTimeMillis: Long,
        existingEventId: Long? = null
    ): Long? {
        if (!hasCalendarPermission(context)) return null

        try {
            val projection = arrayOf(CalendarContract.Calendars._ID)
            val uri: Uri = CalendarContract.Calendars.CONTENT_URI
            val cursor = context.contentResolver.query(
                uri,
                projection,
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                null
            )

            var calendarId: Long = 1
            cursor?.use {
                if (it.moveToFirst()) {
                    calendarId = it.getLong(0)
                }
            }

            val endTimeMillis = startTimeMillis + (15 * 60 * 1000) // 15 minutos de duração

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startTimeMillis)
                put(CalendarContract.Events.DTEND, endTimeMillis)
                put(CalendarContract.Events.TITLE, "📅 [Miix] $title")
                put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.HAS_ALARM, 1)
            }

            if (existingEventId != null && existingEventId > 0) {
                val updateUri = Uri.withAppendedPath(CalendarContract.Events.CONTENT_URI, existingEventId.toString())
                val rows = context.contentResolver.update(updateUri, values, null, null)
                if (rows > 0) return existingEventId
            }

            val newUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            return newUri?.lastPathSegment?.toLongOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
