package app.danebrain.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VaultLayoutTest {
    private val folders = listOf("00-Inbox", "10-Projects", "30-Resources", "40-Archive", "50-Daily", "60-Templates", "90-Attachments")

    @Test fun obsidianAttachmentFolderWins() {
        assertEquals("assets/files", VaultLayout.attachmentRoot("""{"attachmentFolderPath":"assets/files"}""", folders))
    }

    @Test fun rootAndNoteRelativeSettingsFallBackToAnExistingFolder() {
        assertEquals("90-Attachments", VaultLayout.attachmentRoot("""{"attachmentFolderPath":"/"}""", folders))
        assertEquals("90-Attachments", VaultLayout.attachmentRoot("""{"attachmentFolderPath":"./"}""", folders))
        assertEquals("90-Attachments", VaultLayout.attachmentRoot("""{"attachmentFolderPath":"./files"}""", folders))
        assertEquals("90-Attachments", VaultLayout.attachmentRoot("{}", folders))
        assertEquals("90-Attachments", VaultLayout.attachmentRoot(null, folders))
        assertEquals("90-Attachments", VaultLayout.attachmentRoot("not json", folders))
    }

    @Test fun aVaultWithNoAttachmentFolderGetsTheDefault() {
        assertEquals("Attachments", VaultLayout.attachmentRoot(null, listOf("Notes")))
        assertEquals("Attachments", VaultLayout.attachmentRoot("""{"attachmentFolderPath":"../outside"}""", listOf("Notes")))
    }

    @Test fun guessesTheInbox() {
        assertEquals("00-Inbox", VaultLayout.guessInbox(folders))
        assertNull(VaultLayout.guessInbox(listOf("Notes", "Work")))
    }

    @Test fun starterRoutesCoverFilingFoldersOnly() {
        val routes = Routes.parse(VaultLayout.starterRoutesJson("00-Inbox", folders))
        // The test JSON library does not keep key order, so compare sorted.
        assertEquals(listOf("inbox", "projects", "resources"), routes.map { it.key }.sorted())
        assertEquals(listOf("00-Inbox", "10-Projects", "30-Resources"), routes.map { it.folder }.sorted())
        assertEquals("inbox", Routes.default(routes).key)
    }

    @Test fun starterRoutesSurviveOddFolderNames() {
        val routes = Routes.parse(VaultLayout.starterRoutesJson("Inbox", listOf("My \"Stuff\"", "2024", "Work", "work")))
        assertEquals(listOf("2024", "inbox", "my-stuff", "work"), routes.map { it.key }.sorted())
    }

    @Test fun inboxOnlyTableHasOneDatedRoute() {
        val routes = Routes.inboxOnly("Notes/In")
        assertEquals(1, routes.size)
        assertEquals("Notes/In", routes[0].folder)
        assertEquals(true, routes[0].datePrefix)
    }
}
