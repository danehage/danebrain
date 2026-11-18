package app.danebrain.core

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class DatedItemTest {
    @Test fun readsEventsAndDropsUnusableOnes() {
        val items = DatedItem.parse(
            JSONArray(
                """[
                  {"title":"Bill due","date":"2026-11-28","time":""},
                  {"title":"Dentist","date":"2026-11-24","time":"3:05"},
                  {"title":"Bad time","date":"2026-12-01","time":"sometime"},
                  {"title":"","date":"2026-12-01"},
                  {"title":"No date","date":"next week"},
                  {"title":"Not real","date":"2026-02-30"},
                  {"title":"Bill due","date":"2026-11-28","time":""}
                ]"""
            )
        )
        assertEquals(
            listOf(DatedItem("Bill due", "2026-11-28"), DatedItem("Dentist", "2026-11-24", "03:05"), DatedItem("Bad time", "2026-12-01")),
            items,
        )
        assertEquals(emptyList<DatedItem>(), DatedItem.parse(null))
    }

    @Test fun allDayItemsSpanUtcMidnights() {
        val (start, end) = DatedItem("Bill due", "2026-11-28").span(ZoneId.of("America/New_York"))
        assertEquals(1795824000000L, start)
        assertEquals(24 * 60 * 60 * 1000L, end - start)
    }

    @Test fun timedItemsUseTheLocalZoneAndLastAnHour() {
        val (start, end) = DatedItem("Dentist", "2026-11-24", "15:00").span(ZoneId.of("America/New_York"))
        // 15:00 in New York in November is 20:00 UTC.
        assertEquals(1795824000000L - 4 * 24 * 60 * 60 * 1000L + 20 * 60 * 60 * 1000L, start)
        assertEquals(60 * 60 * 1000L, end - start)
    }

    @Test fun describesWhen() {
        assertEquals("Sat 28 Nov 2026", DatedItem("Bill due", "2026-11-28").whenText())
        assertEquals("Tue 24 Nov 2026, 15:00", DatedItem("Dentist", "2026-11-24", "15:00").whenText())
    }

    @Test fun analysisCarriesEventsThroughJson() {
        val raw = """{"route":"inbox","title":"Dentist letter","summary":"s","transcription":"","tags":[],"fields":{},
            "events":[{"title":"Dentist","date":"2026-11-24","time":"15:00"}]}"""
        val analysis = Analysis.parse(raw)
        assertEquals(listOf(DatedItem("Dentist", "2026-11-24", "15:00")), analysis.events)
        assertEquals(analysis.events, Analysis.parse(analysis.toJson()).events)
        assertEquals(emptyList<DatedItem>(), Analysis.parse("""{"title":"No events"}""").events)
    }
}
