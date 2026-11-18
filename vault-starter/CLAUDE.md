# Operating manual for Claude Code

You are working inside a personal Obsidian vault. This is a second brain, not a codebase. Your job is to help the owner **capture, query and synthesize** their own notes, not to write software here.

## Who the owner is

<!-- Replace this section. A short paragraph or five bullets is enough: role, household,
     current projects, interests, anything that changes how notes should be filed. -->
- (fill this in)

## Vault structure

| Folder | What lives here |
|---|---|
| `00-Inbox/` | Fast capture. Unprocessed notes. Triaged weekly. |
| `10-Projects/` | Active projects with a defined outcome. |
| `20-Areas/` | Ongoing responsibilities (finances, family, health, home). |
| `30-Resources/` | Reference material by topic. |
| `40-Archive/` | Finished projects and dormant areas. |
| `50-Daily/` | Daily notes (`YYYY-MM-DD.md`). |
| `60-Templates/` | Source templates. Copy them when creating notes; do not edit them. |
| `90-Attachments/` | Images and scans. |

## Note conventions

- **Filenames:** kebab-case (`lisbon-itinerary.md`).
- **Frontmatter:** YAML at the top of every non-daily note. Always include `type`, `status` and relevant tags.
- **Links:** prefer `[[wikilinks]]` over paths.
- **Dates:** ISO format (`2026-04-14`).

## How the owner uses you

### 1. Capture
When the owner drops a raw thought, structure it into the right folder with proper frontmatter. Ask where it belongs if that is unclear. Default to `00-Inbox/`.

### 2. Query
Questions such as "summarize everything I know about the kitchen renovation" or "pull all action items from this week's daily notes". Use Glob, Grep and Read. Never fabricate: if the vault does not have it, say so.

### 3. Synthesize
Weekly reviews, project status rollups, lessons learned. Always cite the source notes with `[[wikilinks]]` so the owner can jump to them.

## Evergreen notes and linking

Durable lessons get distilled into **evergreen notes**: one idea per note, titled as a claim, created from `60-Templates/evergreen.md` and filed under `30-Resources/<topic>/`.

- **The owner's words are logged verbatim.** The body of an evergreen note is what they actually said or wrote. Your elaboration goes below it in a separate `## Claude's notes` section, never blended into their words.
- **Every new non-daily note gets at least one `[[wikilink]]`, with a clause saying why.** If nothing better exists, link the topic's hub note.
- **At capture time,** search the vault for related notes and suggest connections.
- **When a lesson recurs,** update the existing evergreen (add evidence, sharpen the wording, move `status: seedling` to `established`) instead of creating a near-duplicate.

## Notes that arrive from the phone

The DaneBrain phone app writes new notes straight into this vault. You will see them appear between sessions.

- Its filing destinations are listed in `danebrain-routes.json` at the top of the vault. If the owner adds a folder they want to capture into from the phone, add a route for it there, with a `when` sentence saying what belongs in it.
- Notes it could not analyze are tagged `unprocessed` and sit in `00-Inbox/`. Treat them like any other inbox note at triage.
- Scanned documents embed their image and carry a machine transcription. The scan is the record; the transcription can be wrong, so check figures against the image before relying on them.
- The app only creates files. It never edits a note, so anything that needs appending or linking after capture is your job at the next review.

## What you should not do

- Do not reorganize files without asking. The folder choices are intentional.
- Do not delete anything. Move it to `40-Archive/` instead.
- Do not create summary files unless asked. Answers in the terminal are fine.
- Do not edit `60-Templates/` when creating a note; copy the template.

@AGENTS.md
