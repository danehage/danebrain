package app.danebrain.core

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Something with a date that the model found in a capture: an appointment, a deadline, a due date.
 * It is only ever a suggestion; nothing reaches a calendar unless the user switches it on.
 * [time] is "HH:mm" in 24-hour form, or empty for an all-day item.
 */
data class DatedItem(val title: String, val date: String, val time: String = "") {

    val allDay: Boolean get() = time.isEmpty()

    /**
     * Start and end in epoch milliseconds. Timed items run [DEFAULT_MINUTES] in [zone]. All-day
     * items span UTC midnight to UTC midnight, which is how Android's calendar stores them.
     */
    fun span(zone: ZoneId): Pair<Long, Long> {
        val day = LocalDate.parse(date)
        if (allDay) {
            val start = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            return start to day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val start = day.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()
        return start to start + DEFAULT_MINUTES * 60_000L
    }

    /** For display: "Sat 28 Nov 2026" or "Tue 24 Nov 2026, 15:00". */
    fun whenText(): String {
        val day = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH))
        return if (allDay) day else "$day, $time"
    }

    companion object {
        const val DEFAULT_MINUTES = 60
        private const val MAX_ITEMS = 8
        private val TIME = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$")

        /** Reads the model's "events" array, dropping anything without a title or a real date. */
        fun parse(array: JSONArray?): List<DatedItem> {
            if (array == null) return emptyList()
            val out = ArrayList<DatedItem>()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val title = o.optString("title").trim()
                val date = o.optString("date").trim()
                if (title.isEmpty() || runCatching { LocalDate.parse(date) }.isFailure) continue
                val match = TIME.find(o.optString("time").trim())
                val time = match?.let { "%02d:%s".format(it.groupValues[1].toInt(), it.groupValues[2]) }.orEmpty()
                val item = DatedItem(title.take(120), date, time)
                if (item !in out) out.add(item)
            }
            return out.take(MAX_ITEMS)
        }

        fun toJson(items: List<DatedItem>): JSONArray {
            val array = JSONArray()
            items.forEach { array.put(JSONObject().put("title", it.title).put("date", it.date).put("time", it.time)) }
            return array
        }
    }
}
