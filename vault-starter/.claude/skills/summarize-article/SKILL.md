---
name: summarize-article
description: Fetch a web article, summarize it, and file the summary as a note in 00-Inbox/. Use when the owner says "summarize this article", "file this article", or shares a link to (or pastes the text of) a longform article and asks for a summary or notes on it.
---

# Summarize article

Turn an article link or pasted text into a summary note the owner can triage. The aim is to save reading time: pull out what is useful, say plainly what is filler, and let them decide from the note whether the full piece is worth it.

## Workflow

1. **Get the text.** Fetch the URL and capture the title, author, publication and date. If the page is paywalled or will not load, ask the owner to paste the text. If text was pasted with no link, record `source: pasted`.

2. **Summarize from the article only.** Do not pad with outside knowledge, and do not attribute claims the article does not make. If the text looks cut off or mangled, say so.

3. **Search the vault for related notes** so the summary can link to them.

4. **Write the note** as `article-<short-topic>.md` in `00-Inbox/`, using the format below. Every note needs at least one `[[wikilink]]` with a reason.

5. **Report back:** the file path, a one-line verdict, and which parts justify a full read.

## Note format

```markdown
---
type: article-summary
status: inbox
source: <url, or `pasted`>
title: "<headline>"
author: <if known>
publication: <if known>
published: YYYY-MM-DD
captured: YYYY-MM-DD
tags: [<topic tags>]
---

## Verdict
One or two sentences: is this worth reading in full, which parts, and why.

## Key ideas
- The three to six points that matter, each in a sentence or two.

## Worth quoting
> Any line worth keeping verbatim.

## Connects to
- [[related-note]], because ...
```
