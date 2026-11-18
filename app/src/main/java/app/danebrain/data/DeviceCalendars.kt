package app.danebrain.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import app.danebrain.core.DatedItem
import java.io.IOException
import java.time.ZoneId

/** A calendar on this phone that events can be added to. */
data class DeviceCalendar(val id: Long, val name: String, val account: String)

/**
 * Adds events to a calendar already on the phone, through Android's calendar storage. The phone's
 * own account sync then carries them wherever that calendar goes (Google Calendar, and from there
 * a shared wall display, for example). No sign-in or network call happens here.
 */
object DeviceCalendars {
    val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    fun hasPermission(context: Context): Boolean = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Calendars the user can add events to, the account's main calendar first. */
    fun writable(context: Context): List<DeviceCalendar> {
        if (!hasPermission(context)) return emptyList()
        val columns = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        val where = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"
        val found = ArrayList<Pair<Boolean, DeviceCalendar>>()
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, columns, where, null, null)?.use { c ->
            while (c.moveToNext()) {
                found.add((c.getInt(3) == 1) to DeviceCalendar(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty()))
            }
        }
        return found.sortedWith(compareByDescending<Pair<Boolean, DeviceCalendar>> { it.first }.thenBy { it.second.name }).map { it.second }
    }

    /** Adds one event with the calendar's default reminder. [description] goes in the event's notes. */
    fun insert(context: Context, calendarId: Long, item: DatedItem, description: String) {
        if (!hasPermission(context)) throw IOException("Calendar permission is off")
        val zone = ZoneId.systemDefault()
        val (start, end) = item.span(zone)
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, item.title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.ALL_DAY, if (item.allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, if (item.allDay) "UTC" else zone.id)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: throw IOException("The calendar did not accept the event")
        val eventId = uri.lastPathSegment?.toLongOrNull() ?: return
        val reminder = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, CalendarContract.Reminders.MINUTES_DEFAULT)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_DEFAULT)
        }
        runCatching { context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder) }
    }
}
