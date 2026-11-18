package app.danebrain.core

import app.danebrain.core.Markdown.Code
import app.danebrain.core.Markdown.Embed
import app.danebrain.core.Markdown.Heading
import app.danebrain.core.Markdown.ListItem
import app.danebrain.core.Markdown.Paragraph
import app.danebrain.core.Markdown.Quote
import app.danebrain.core.Markdown.Rule
import app.danebrain.core.Markdown.Span
import app.danebrain.core.Markdown.Table
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTest {
    @Test fun readsFrontmatterAndBody() {
        val doc = Markdown.parse(
            "---\ntype: document\ntags: [document, bill]\naliases:\n  - one\n  - two\n---\n\n# Acme Power bill\n\nFiled under [[finances]].\n"
        )
        assertEquals(listOf("type" to "document", "tags" to "[document, bill]", "aliases" to "one, two"), doc.properties)
        assertEquals(listOf(Heading(1, "Acme Power bill"), Paragraph("Filed under [[finances]].")), doc.blocks)
    }

    @Test fun aRuleAtTheTopIsNotFrontmatterWithoutAClosingLine() {
        val doc = Markdown.parse("---\njust text")
        assertEquals(emptyList<Pair<String, String>>(), doc.properties)
        assertEquals(listOf(Rule, Paragraph("just text")), doc.blocks)
    }

    @Test fun readsBlocks() {
        val doc = Markdown.parse(
            """
            ## Summary
            Two lines
            in one paragraph.

            - first
              - nested
            - [ ] to do
            - [x] done
            1. numbered

            > quoted
            > again

            ![[2026-11-03-acme-power-bill-p1.jpg]]

            ```
            code *stays* raw
            ```

            ***

            | Item | Cost |
            |---|---:|
            | Tap | 400 |
            """.trimIndent()
        )
        assertEquals(
            listOf(
                Heading(2, "Summary"),
                Paragraph("Two lines\nin one paragraph."),
                ListItem(0, null, null, "first"),
                ListItem(1, null, null, "nested"),
                ListItem(0, null, false, "to do"),
                ListItem(0, null, true, "done"),
                ListItem(0, "1.", null, "numbered"),
                Quote("quoted\nagain"),
                Embed("2026-11-03-acme-power-bill-p1.jpg"),
                Code("code *stays* raw"),
                Rule,
                Table(listOf(listOf("Item", "Cost"), listOf("Tap", "400"))),
            ),
            doc.blocks,
        )
    }

    @Test fun dropsHtmlComments() {
        val doc = Markdown.parse("## Outcome\n<!-- What does done look like?\nBe specific. -->\nShip it.")
        assertEquals(listOf(Heading(2, "Outcome"), Paragraph("Ship it.")), doc.blocks)
    }

    @Test fun readsInlineStyles() {
        assertEquals(
            listOf(
                Span("See "),
                Span("finances", link = "finances", linkIsNote = true),
                Span(" and "),
                Span("the bill", link = "20-Areas/finances/bill", linkIsNote = true),
                Span(", "),
                Span("bold", bold = true),
                Span(" "),
                Span("it", italic = true),
                Span(" "),
                Span("code", code = true),
                Span(" "),
                Span("site", link = "https://example.com", linkIsNote = false),
                Span("."),
            ),
            Markdown.inline("See [[finances]] and [[20-Areas/finances/bill|the bill]], **bold** *it* `code` [site](https://example.com)."),
        )
    }

    @Test fun nestsStylesAndLeavesPlainUnderscoresAlone() {
        assertEquals(
            listOf(Span("a ", bold = true), Span("link", bold = true, link = "note", linkIsNote = true)),
            Markdown.inline("**a [[note|link]]**"),
        )
        assertEquals(listOf(Span("snake_case_name and 2 * 3 * 4")), Markdown.inline("snake_case_name and 2 * 3 * 4"))
        assertEquals(listOf(Span("section", link = "note", linkIsNote = true)), Markdown.inline("[[note#heading|section]]"))
    }
}
