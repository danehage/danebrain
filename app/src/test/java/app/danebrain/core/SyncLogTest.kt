package app.danebrain.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncLogTest {
    @Test
    fun notesWrittenAfterObsidianWasOpenAreWaiting() {
        var log = ""
        log = SyncLog.add(log, "00-Inbox/a.md", 100)
        log = SyncLog.add(log, "00-Inbox/b.md", 300)
        assertEquals(listOf("00-Inbox/a.md", "00-Inbox/b.md"), SyncLog.waiting(log, 50))
        assertEquals(listOf("00-Inbox/b.md"), SyncLog.waiting(log, 200))
        assertEquals(emptyList<String>(), SyncLog.waiting(log, 300))
    }

    @Test
    fun pruneDropsNotesObsidianHasSeen() {
        var log = SyncLog.add("", "a.md", 100)
        log = SyncLog.add(log, "b.md", 300)
        val pruned = SyncLog.prune(log, 200)
        assertEquals(listOf("b.md"), SyncLog.waiting(pruned, 0))
    }

    @Test
    fun damagedLogCountsAsEmpty() {
        assertEquals(emptyList<String>(), SyncLog.waiting("not json", 0))
        assertEquals(listOf("a.md"), SyncLog.waiting(SyncLog.add("not json", "a.md", 5), 0))
    }
}
