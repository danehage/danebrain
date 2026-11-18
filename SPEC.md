# DaneBrain app: spec

Status: first build, 2026-11-17. Capture (text, voice, scan, share), search and chat are implemented; the code compiles and unit tests pass, but the app has not yet been run on a phone. Where the build differs from this spec, see section 11.

A sideloaded Android app for one user. It turns paper into vault notes, and later adds fast search and a chatbot over the vault. It reads and writes the Obsidian vault folder on the phone directly; Obsidian Sync carries everything to the PC.

## 1. Goals

1. **Scan to vault.** Photograph a wine label, handwritten note, invoice or bill, and get a properly filed note with the scan embedded, in under 30 seconds of attention.
2. **Search.** Instant, offline full-text search over the vault.
3. **Chat.** Ask questions of the vault through an LLM that can search and read notes.

Not goals: editing existing notes, replacing Obsidian mobile, multi-user support, Play Store distribution, iOS.

## 2. Principles

- **The scan is the source of truth.** The paper gets thrown away, so every note embeds the original image. Model output (transcription, extracted amounts) is a searchable convenience and can be wrong.
- **Never lose a capture.** A scan is durable on the phone the moment it is taken, whether or not there is signal or the model works. (Carried over from an earlier capture pipeline.)
- **Create only.** The app creates new files in the vault. It never edits, moves or deletes an existing file, and never touches `.obsidian/`. This rules out sync conflicts.
- **The app decides paths, not the model.** The model picks a note type and proposes a title; code maps that to a folder and filename. (Also carried over.)
- **Vault conventions hold.** Kebab-case filenames, ISO dates, YAML frontmatter with `type` and `status`, at least one wikilink on anything filed outside the inbox.

## 3. How it fits with what exists

- **Obsidian mobile + Obsidian Sync** stay as they are. Sync only runs while Obsidian is open, so a capture reaches the PC the next time Obsidian mobile is opened.
- **An earlier capture pipeline** (Cloudflare Worker, Gemini, the Advanced URI plugin) handled voice and text capture. It is not in working condition and this app replaces it: M2 covers text and voice capture natively, with no worker, no capture token and no plugin. Its route table was the starting point for this app's, and two of its rules carry over (section 2).

## 4. Milestones

| # | Milestone | Outcome |
|---|---|---|
| M0 | Feasibility spike | Three checks in section 9 pass on the real phone |
| M1 | Scan to vault | Capture, queue, review and filing for the four document types |
| M2 | Quick capture | Text and voice note screen, Android share target (text, links, images) |
| M3 | Search | Local full-text index and search screen |
| M4 | Chat | LLM with search and read tools over the vault |

M1 is specified in detail below. M2 to M4 are outlines and get their own detail when reached.

## 5. M1: scan to vault

### 5.1 Flow

1. **Scan.** The app opens on the scanner. One or more pages per capture. Optional text field for the user's own words (for wine, the tasting note).
2. **Stage.** Pages are saved to app-private storage and the capture is added to a queue. Nothing is in the vault yet.
3. **Analyze.** A background job sends the pages to a vision model and stores the result. It retries when offline or on failure.
4. **Review.** One screen shows the scan, proposed type, title, extracted fields and destination folder. Every field is editable. Accept writes to the vault.
5. **Write.** Images first, then the note, so a note never points at a missing image.

A queued capture can be saved at any time without analysis: it goes to `00-Inbox/` tagged `unprocessed`, with the scan embedded and a title the user types. The queue shows a count on the home screen so nothing sits unnoticed.

### 5.2 Scanning and images

- Google ML Kit document scanner: edge detection, crop, deskew, multi-page, gallery import. On-device and free; needs Google Play services.
- Output: JPEG, long edge about 2000 px, quality tuned to land at 300 to 500 KB per page. Obsidian Sync caps file size and total storage, so originals are not kept.
- Image filenames: `<note-slug>-p1.jpg`, `<note-slug>-p2.jpg`.
- Image folder: `90-Attachments/scans/<year>/`, created by the app on first write. The same folder is set as Obsidian's attachment folder so images added by hand land there too.

### 5.3 Analysis

- One call to a vision model through OpenRouter, with a JSON schema response.
- Input: page images, the user's text if any, and the route table (names and descriptions only; no vault content).
- Output: `doc_type`, `title`, `transcription`, `summary`, and the fields for that type.
- Model is a setting. Default: a fast, low-cost vision model; chosen during M1 by testing on real scans of each type, handwriting especially.
- OpenRouter is configured to exclude providers that train on inputs. Bills carry account numbers and the home address.

### 5.4 Document types

| Type | Destination | `type` / `status` | Extracted fields |
|---|---|---|---|
| Wine label | `30-Resources/wine/bottles/` | `wine-bottle` / `logged` | producer, wine, vintage, grapes, place, country, color |
| Invoice or bill | `20-Areas/finances/documents/` by default; the review screen can pick a topic folder instead | `document` / `filed` | vendor, doc_kind, amount, currency, doc_date, due_date, account_last4 |
| Handwritten note | `00-Inbox/` | `note` / `inbox` | none |
| Other paper | `00-Inbox/` | `note` / `inbox` | none |

Filenames:

- Wine: `<vintage>-<producer>-<wine>.md`, matching the existing bottles (`2022-juan-gil-monastrell.md`).
- Invoice or bill: `<doc_date>-<vendor>-<doc_kind>.md`.
- Inbox: `<capture_date>-<title-slug>.md`.
- If the name exists, append `-2`, `-3`. Never overwrite.

### 5.5 Note formats

**Wine bottle.** Follows `60-Templates/wine-bottle.md` exactly, so the note shows up in `bottles.base`.

- Frontmatter filled from the label. `drunk_on` defaults to today. `price`, `where`, `served`, `again` are optional fields on the review screen.
- `grapes`, `place`: written as wikilinks only when a note with that name already exists in the vault; plain text otherwise.
- `## In my words`: the typed text, verbatim, in a quote block with the date. Never rewritten by the model. Empty quote if nothing was typed. The heading text is set per route (`wordsHeading`) so it can match an existing template.
- Label scan embedded under the title.
- The research sections (`What's in the glass`, `Why it tasted that way`, `Connections`, `If you liked this, try`) are left as empty headings. The note gets the tag `needs-enrichment` so a desktop Claude Code session can find and fill them.

**Invoice or bill.**

```markdown
---
type: document
status: filed
doc_kind: bill
vendor: Acme Power
amount: 184.32
currency: USD
doc_date: 2026-11-03
due_date: 2026-11-28
account_last4: "1234"
captured: 2026-11-17
tags: [document, bill]
---

# Acme Power bill, 2026-11-03

Filed under [[finances]] because it is a household bill.

![[2026-11-03-acme-power-bill-p1.jpg]]

## Summary
One or two lines from the model.

## Transcription
Full text from the model. May contain errors; the scan above is the record.
```

**Inbox note.** Frontmatter `type: note`, `status: inbox`, `captured`, `tags`. Body: title, embedded scan, transcription. No forced wikilink; linking happens at triage.

### 5.6 Settings

- Vault folder path (picked once).
- OpenRouter API key, held in Android Keystore-backed storage.
- Model name.
- Route table: bundled JSON seeded from `vault-routes.json`, editable in a later milestone.

## 6. M2 to M4 outlines

- **M2 quick capture.** Replaces the earlier capture pipeline.
  - A text screen with a large multi-line field; voice input is Gboard dictation into that field, so there is no cutoff and no speech code to write.
  - Two save paths: "Save raw" writes straight to `00-Inbox/` with no model call and works offline; "Save" sends the text to the model for a title, tags and a route, then shows the same review screen as scans.
  - Text captures use the same queue as scans, so a failed or offline model call never loses the text.
  - A share target that accepts text, links and images from other apps and feeds the same queue.
  - A home screen shortcut that opens directly on the text field.
- **M3 search.** SQLite FTS index of all markdown, built on first run and refreshed by comparing file modified times on app open. Results show title, folder and a snippet; tapping opens the note in Obsidian through an `obsidian://open` link.
- **M4 chat.** OpenRouter chat with three tools run on the device: search the index, list a folder, read a note. `AGENTS.md` from the vault is the system prompt. Answers cite notes, and citations open in Obsidian. No embeddings in the first version; add them only if the tool loop proves too weak for fuzzy questions.

## 7. Tech stack

- Kotlin, Jetpack Compose, single-activity.
- ML Kit document scanner for capture.
- Room for the capture queue (and the FTS index in M3).
- WorkManager for background analysis with retry.
- OkHttp and kotlinx.serialization for OpenRouter.
- File access: `MANAGE_EXTERNAL_STORAGE` (all files access). Acceptable for a sideloaded personal app and much simpler than the Storage Access Framework for walking a vault.
- Build: debug-signed APK installed over USB with `adb install`.

## 8. Failure handling

| Failure | Behavior |
|---|---|
| Offline at scan time | Capture is staged; analysis runs when the network returns |
| Model error, timeout or malformed JSON | Retry with backoff; after three failures the capture is marked "needs manual" and can be saved to the inbox as `unprocessed` |
| Model picks the wrong type | User changes the type on the review screen; fields and destination update |
| Filename collision | Numeric suffix |
| Vault folder missing or unreadable | Captures stay in the queue; the home screen shows the problem |
| Write fails partway | Images are written before the note; a retry reuses the same names and skips files that already match |

## 9. M0: checks before building

1. **The vault is reachable.** On Android, an Obsidian vault can live in shared device storage or in Obsidian's private app storage. Only the first is readable by another app. If the vault is in app storage it must be moved to device storage first.
2. **Obsidian Sync picks up outside files.** Copy a markdown file and a JPEG into the vault with a file manager, open Obsidian mobile, and confirm both arrive on the PC.
3. **Image sync is on.** Confirm images are enabled in Sync's selective sync settings on both devices, and note the plan's file size and storage limits.

## 10. Decisions

Working defaults. These are built into the app but have not been confirmed; each is a one-line change (`NoteBuilder.IMAGE_ROOT`, `routes.json`).

1. **Image folder.** A new top-level `90-Attachments/scans/<year>/`. Setting it as Obsidian's attachment folder too is a manual step, not done.
2. **Home for invoices and bills.** A new `20-Areas/finances/documents/` as the default. The review screen can switch the route, but only among the routes in `routes.json`; an arbitrary topic folder needs a new route.
3. **Wine research.** Enrichment is left to desktop sessions; the app tags bottle notes `needs-enrichment`.

Still open:

1. **Vendor rules.** Later option: per-vendor defaults, such as invoices from one vendor tagged as reimbursable.

## 11. Where the build differs from this spec

- **Home screen.** The app opens on a capture screen with three actions (Scan paper, Speak, Write), the draft in progress and the queue, not directly on the scanner. Write opens a full-page editor. A long-press launcher shortcut starts a scan directly.
- **Voice.** In-app dictation through Android's `SpeechRecognizer` (on-device where available), not Gboard dictation. A mic button sits in the middle of the tab bar on every tab; listening is a full-screen view that restarts the recognizer across pauses and ends on a tap or after three silent rounds, then opens the editor with the words.
- **Look.** Fixed light and dark themes in a warm paper palette (cream, ink, browns, teal accent), with Fraunces for titles. Settings has a Follow phone / Light / Dark choice; the default follows the phone. No wallpaper-based dynamic color.
- **Text captures.** The model supplies only the title, tags and route. The note body is the typed text, unchanged.
- **Inbox frontmatter** uses `date:` (matching existing inbox notes), not `captured:`.
- **Routes.** The route table is a `danebrain-routes.json` file at the top of the vault, so the folders a person files into are their own data and not part of the app. Without that file, everything goes to a single inbox folder the user picks, and the app can write a starter file from the vault's top-level folders. The repository's `vault-starter/` has a ready-made table. There is no `daily` route (the app cannot append to an existing daily note).
- **Review screen** edits title, route, fields, tags, summary, transcription and the user's words. The filename is derived, not editable.
- **Storage.** Plain `SQLiteOpenHelper` instead of Room. The search table is FTS5 with ranked results, falling back to FTS4 where FTS5 is missing.
- **Index refresh** runs each time the app comes to the foreground, and on demand.
- **Chat** history lives in memory and is lost when the app is closed.
- **Route table** is not editable in the app; edit `danebrain-routes.json` in the vault.
- **Image folder.** Scans go to `<attachments>/scans/<year>/`, where `<attachments>` is Obsidian's configured attachment folder when it names a fixed folder, else an existing top-level folder with "attachments" in its name, else `Attachments`.
- **First run.** A setup screen walks through all-files access, choosing the vault (auto-detect, a folder picker or a typed path), the inbox folder and the API key, with a button that tests the key.
- **Model endpoint.** The API address is a setting, so any OpenAI-compatible service can be used. The no-retention provider filter is only sent to OpenRouter.
- **Note reader.** Search results and chat citations open in a built-in read-only Markdown reader (`core/Markdown.kt`, `ui/NoteScreen.kt`) that follows wikilinks and shows embedded images; a button opens the note in Obsidian.
- **Not built:** per-vendor rules, embeddings.
