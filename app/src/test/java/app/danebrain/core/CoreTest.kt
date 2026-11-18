package app.danebrain.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class CoreTest {

    private val routesJson = """
        {
          "inbox": { "folder": "00-Inbox", "type": "note", "status": "inbox", "datePrefix": true, "when": "default" },
          "wine-bottle": { "folder": "30-Resources/wine/bottles", "type": "wine-bottle", "status": "logged", "format": "wine-bottle", "tags": ["wine"], "wordsHeading": "In Sam's words", "when": "a bottle" },
          "document": { "folder": "20-Areas/finances/documents", "type": "document", "status": "filed", "format": "document", "tags": ["document"], "hub": "finances", "hubReason": "it is a household paper record", "when": "a bill" },
          "bad": { "folder": "../outside", "when": "never" },
          "empty": { "folder": "", "when": "never" }
        }
    """.trimIndent()
    private val routes = Routes.parse(routesJson)
    private fun route(key: String) = routes.first { it.key == key }

    private fun tempVault(): File = Files.createTempDirectory("vault").toFile().also { File(it, ".obsidian").mkdirs() }

    // --- Text ---

    @Test fun slugifyMakesKebabCase() {
        assertEquals("portugal-itinerary", Text.slugify("Portugal Itinerary!"))
        assertEquals("cotes-du-rhone-2019", Text.slugify("Côtes du Rhône 2019"))
        assertEquals("strasse", Text.slugify("Straße"))
        assertEquals("", Text.slugify("???"))
    }

    @Test fun slugifyCutsAtWordBoundary() {
        val slug = Text.slugify("one two three four five six seven eight nine ten eleven twelve thirteen", 30)
        assertTrue(slug.length <= 30)
        assertFalse(slug.endsWith("-"))
        assertTrue("one-two-three-four-five-six-seven-eight".startsWith(slug))
    }

    @Test fun titleFromTextUsesFirstLine() {
        assertEquals("Call the dentist tomorrow", Text.titleFromText("\n  call the dentist tomorrow.\nsecond line"))
        assertEquals("Untitled note", Text.titleFromText("   "))
    }

    @Test fun yamlQuotesOnlyWhenNeeded() {
        assertEquals("Acme Power", Text.yamlString("Acme Power"))
        assertEquals("\"AT&T: wireless\"", Text.yamlString("AT&T: wireless"))
        assertEquals("\"yes\"", Text.yamlString("yes"))
        assertEquals("\"2022\"", Text.yamlString("2022"))
        assertEquals("\"say \\\"hi\\\"\"", Text.yamlString("say \"hi\""))
        assertEquals("", Text.yamlString("  "))
    }

    @Test fun parseAmountHandlesSeparators() {
        assertEquals("184.32", Text.parseAmount("$184.32"))
        assertEquals("1234.56", Text.parseAmount("1,234.56"))
        assertEquals("1234.56", Text.parseAmount("1.234,56 EUR"))
        assertEquals("184.32", Text.parseAmount("184,32"))
        assertNull(Text.parseAmount("n/a"))
    }

    // --- Routes ---

    @Test fun routesSkipInvalidEntriesAndFallBackToInbox() {
        assertEquals(setOf("inbox", "wine-bottle", "document"), routes.map { it.key }.toSet())
        assertEquals("inbox", Routes.find(routes, "nonsense").key)
        assertEquals("inbox", Routes.find(routes, null).key)
        assertEquals("document", Routes.find(routes, "document").key)
    }

    // --- Analysis ---

    @Test fun analysisParsesFencedJsonAndNormalizes() {
        val raw = "Here you go:\n```json\n{\"route\":\"document\",\"title\":\"Acme Power bill\",\"summary\":\"s\"," +
            "\"transcription\":\"t\",\"tags\":[\"Electric Bill\",\"electric-bill\"],\"fields\":{\"amount\":184.32,\"due_date\":null,\"grapes\":[\"a\",\"b\"]}}\n```"
        val a = Analysis.parse(raw)
        assertEquals("document", a.route)
        assertEquals(listOf("electric-bill"), a.tags)
        assertEquals("184.32", a.fields["amount"])
        assertFalse(a.fields.containsKey("due_date"))
        assertEquals("a, b", a.fields["grapes"])
        assertEquals(a, Analysis.parse(a.toJson()))
    }

    @Test fun analysisRejectsRepliesWithoutJsonOrTitle() {
        assertThrows(IllegalArgumentException::class.java) { Analysis.parse("sorry, I cannot") }
        assertThrows(IllegalArgumentException::class.java) { Analysis.parse("{\"route\":\"inbox\",\"title\":\" \"}") }
    }

    @Test fun promptListsRoutesButNoFolders() {
        val prompt = Analysis.systemPrompt(routes, "2026-11-17")
        assertTrue(prompt.contains("- document: a bill"))
        assertFalse(prompt.contains("20-Areas"))
    }

    // --- NoteBuilder ---

    @Test fun inboxNoteKeepsUserTextVerbatim() {
        val words = "pick up   milk\n\n- and EGGS??"
        val d = NoteDraft(route = route("inbox"), title = "Pick up milk", userText = words, tags = listOf("errands"), date = "2026-11-17")
        assertEquals("2026-11-17-pick-up-milk", NoteBuilder.stem(d))
        val md = NoteBuilder.render(d, NoteBuilder.stem(d))
        assertTrue(md.startsWith("---\ntype: note\nstatus: inbox\ndate: 2026-11-17\ntags: [errands]\n---\n\n# Pick up milk\n"))
        assertTrue(md.contains(words))
        assertFalse(md.contains("## Transcription"))
    }

    @Test fun unprocessedNotesAreTagged() {
        val d = NoteDraft(route = route("inbox"), title = "Scan", pageCount = 1, date = "2026-11-17", unprocessed = true)
        val md = NoteBuilder.render(d, "2026-11-17-scan")
        assertTrue(md.contains("tags: [unprocessed]"))
        assertTrue(md.contains("![[2026-11-17-scan-p1.jpg]]"))
    }

    @Test fun documentNoteHasTypedFrontmatterAndHubLink() {
        val d = NoteDraft(
            route = route("document"),
            title = "Acme Power bill",
            summary = "Electric bill for October.",
            transcription = "Total due ${'$'}184.32",
            fields = mapOf(
                "vendor" to "Acme Power", "doc_kind" to "Bill", "amount" to "$184.32", "currency" to "usd",
                "doc_date" to "2026-11-03", "due_date" to "Nov 28", "account_last4" to "xxxx-1234",
            ),
            pageCount = 2,
            date = "2026-11-17",
        )
        val stem = NoteBuilder.stem(d)
        assertEquals("2026-11-03-acme-power-bill", stem)
        val md = NoteBuilder.render(d, stem)
        assertTrue(md.contains("type: document\nstatus: filed\ndoc_kind: bill\nvendor: Acme Power\namount: 184.32\ncurrency: USD\ndoc_date: 2026-11-03\ndue_date: \naccount_last4: \"1234\"\ncaptured: 2026-11-17\ntags: [document, bill]\n"))
        assertTrue(md.contains("Filed under [[finances]] because it is a household paper record."))
        assertTrue(md.contains("![[2026-11-03-acme-power-bill-p1.jpg]]\n![[2026-11-03-acme-power-bill-p2.jpg]]"))
        assertTrue(md.contains("## Summary\n\nElectric bill for October."))
        assertTrue(md.contains("## Transcription"))
    }

    @Test fun documentWithoutVendorFallsBackToTitle() {
        val d = NoteDraft(route = route("document"), title = "Town tax letter", date = "2026-11-17")
        assertEquals("2026-11-17-town-tax-letter", NoteBuilder.stem(d))
    }

    @Test fun wineNoteFollowsBottleTemplate() {
        val words = "loud, fruit forward, but sharp.\nreally liked it"
        val d = NoteDraft(
            route = route("wine-bottle"),
            title = "Juan Gil Monastrell",
            fields = mapOf(
                "producer" to "Bodegas Juan Gil", "wine" to "Monastrell", "vintage" to "2022",
                "grapes" to "Monastrell, Syrah", "place" to "Jumilla", "country" to "Spain", "color" to "Red",
            ),
            userText = words,
            tags = listOf("ignored"),
            pageCount = 1,
            date = "2026-11-17",
        )
        val stem = NoteBuilder.stem(d)
        assertEquals("2022-bodegas-juan-gil-monastrell", stem)
        val md = NoteBuilder.render(d, stem) { it == "monastrell" || it == "jumilla" }
        assertTrue(md.contains("type: wine-bottle\nstatus: logged\nproducer: Bodegas Juan Gil\nwine: Monastrell\nvintage: 2022\n"))
        assertTrue(md.contains("grapes: [\"[[monastrell]]\", Syrah]\n"))
        assertTrue(md.contains("place: \"[[jumilla]]\"\n"))
        assertTrue(md.contains("color: red\n"))
        assertTrue(md.contains("drunk_on: 2026-11-17\n"))
        assertTrue(md.contains("tags: [wine, needs-enrichment]\n"))
        assertTrue(md.contains("# 2022 Bodegas Juan Gil Monastrell\n"))
        assertTrue(md.contains("## In Sam's words\n\n**2026-11-17**\n> \"loud, fruit forward, but sharp.\n> really liked it\"\n"))
        for (heading in listOf("## What's in the glass", "## Why it tasted that way", "## Connections", "## If you liked this, try")) {
            assertTrue(heading, md.contains(heading))
        }
    }

    @Test fun wineNoteWithoutWordsLeavesEmptyQuote() {
        val d = NoteDraft(route = route("wine-bottle"), title = "Mystery red", date = "2026-11-17")
        assertEquals("mystery-red", NoteBuilder.stem(d))
        assertTrue(NoteBuilder.render(d, "mystery-red").contains("> \"\"\n"))
    }

    // --- VaultWriter ---

    @Test fun writerCreatesNoteAndImages() {
        val vault = tempVault()
        val page = File.createTempFile("page", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val d = NoteDraft(route = route("inbox"), title = "Receipt", date = "2026-11-17")
        val r = VaultWriter(vault).write(d, listOf(page, page))
        assertEquals("00-Inbox/2026-11-17-receipt.md", r.notePath)
        assertEquals(
            listOf("90-Attachments/scans/2026/2026-11-17-receipt-p1.jpg", "90-Attachments/scans/2026/2026-11-17-receipt-p2.jpg"),
            r.imagePaths,
        )
        val note = File(vault, r.notePath).readText()
        assertTrue(note.contains("![[2026-11-17-receipt-p2.jpg]]"))
        assertEquals(3, File(vault, r.imagePaths[0]).length())
        assertTrue("no temp files left", vault.walkTopDown().none { it.name.endsWith(".tmp") })
    }

    @Test fun writerNeverOverwrites() {
        val vault = tempVault()
        val existing = File(vault, "00-Inbox/2026-11-17-receipt.md").apply {
            parentFile!!.mkdirs()
            writeText("original")
        }
        val d = NoteDraft(route = route("inbox"), title = "Receipt", date = "2026-11-17")
        val first = VaultWriter(vault).write(d, emptyList())
        val second = VaultWriter(vault).write(d, emptyList())
        assertEquals("00-Inbox/2026-11-17-receipt-2.md", first.notePath)
        assertEquals("00-Inbox/2026-11-17-receipt-3.md", second.notePath)
        assertEquals("original", existing.readText())
    }

    @Test fun writerSkipsStemWhoseImageNameIsTaken() {
        val vault = tempVault()
        File(vault, "90-Attachments/scans/2026/2026-11-17-receipt-p1.jpg").apply {
            parentFile!!.mkdirs()
            writeText("old scan")
        }
        val page = File.createTempFile("page", ".jpg").apply { writeBytes(byteArrayOf(9)) }
        val d = NoteDraft(route = route("inbox"), title = "Receipt", date = "2026-11-17")
        val r = VaultWriter(vault).write(d, listOf(page))
        assertEquals("00-Inbox/2026-11-17-receipt-2.md", r.notePath)
        assertEquals("old scan", File(vault, "90-Attachments/scans/2026/2026-11-17-receipt-p1.jpg").readText())
    }

    @Test fun writerCleansUpItsOwnFilesOnFailure() {
        val vault = tempVault()
        val page = File.createTempFile("page", ".jpg").apply { writeBytes(byteArrayOf(1)) }
        val missing = File(vault, "does-not-exist.jpg")
        val d = NoteDraft(route = route("inbox"), title = "Receipt", date = "2026-11-17")
        assertThrows(IOException::class.java) { VaultWriter(vault).write(d, listOf(page, missing)) }
        assertFalse(File(vault, "90-Attachments/scans/2026/2026-11-17-receipt-p1.jpg").exists())
        assertFalse(File(vault, "00-Inbox/2026-11-17-receipt.md").exists())
    }

    @Test fun writerRefusesMissingVaultAndEscapingPaths() {
        val d = NoteDraft(route = route("inbox"), title = "x", date = "2026-11-17")
        assertThrows(IOException::class.java) { VaultWriter(File("no-such-vault-folder")).write(d, emptyList()) }
        val escaping = route("inbox").copy(folder = "../elsewhere")
        assertThrows(IOException::class.java) { VaultWriter(tempVault()).write(d.copy(route = escaping), emptyList()) }
    }

    // --- SearchQuery ---

    @Test fun searchQueryIsSafeForFullTextMatch() {
        assertEquals("douro producers*", SearchQuery.build("Douro producers"))
        assertEquals("and or not*", SearchQuery.build("AND OR \"NOT\""))
        assertEquals("nflx OR puts", SearchQuery.build("NFLX puts", anyWord = true, prefixLast = false))
        assertEquals("rhône*", SearchQuery.build("Rhône"))
        assertNull(SearchQuery.build("  --- "))
    }
}
