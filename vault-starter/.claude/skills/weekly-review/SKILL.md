---
name: weekly-review
description: Run the weekly vault review. Sweeps the week's daily notes, triages the inbox, and proposes evergreen notes, then presents one digest for approval. Use when the owner says "weekly review", "run my weekly review", or asks for a status rollup of the vault.
---

# Weekly review

Run the whole review in one pass: gather findings read-only, present one digest, then carry out only what the owner approves.

## Workflow

1. **Establish the window.** The last 7 days, or since the previous review if the owner says so. Work out the ISO date range first.

2. **Gather, read-only.** Use parallel sub-agents if they are available; otherwise do the sweeps in turn. Nothing is edited in this step.
   - **Daily sweep:** read `50-Daily/YYYY-MM-DD.md` for the window. Collect unfinished action items (verbatim, with the source date), completed wins, and recurring themes.
   - **Inbox triage:** list every note in `00-Inbox/`. For each: a one-line gist, a suggested destination (`10-Projects/`, `20-Areas/`, `30-Resources/`, `40-Archive/`, or "stay"), and any missing frontmatter or missing `[[wikilinks]]`. Notes tagged `unprocessed` came from the phone without analysis and need a title and a home.
   - **Evergreen pass:** look through the window's daily notes and existing evergreen notes in `30-Resources/`. Collect lessons that recur or generalize, and any non-daily notes with no wikilinks.

3. **Present the digest in the terminal.** No file is created.
   - **Needs a decision:** inbox moves and evergreen proposals, numbered so they can be approved by number.
   - **Open loops:** unfinished action items.
   - **The week that was:** wins and themes, kept short.
   - Cite `[[wikilinks]]` throughout.

4. **Carry out only what is approved.** A moved note gets its `status` updated to match its new home. Evergreen notes use `60-Templates/evergreen.md` and must be in the owner's own words: draft the claim as a title, quote their phrasing from the source notes for the body, and ask them to confirm before writing.

5. **Close the loop.** Add a one-line `## Weekly review` entry to today's daily note saying the review ran and what was decided.

## Rules

- All writes happen after approval.
- Never fabricate. If a sweep comes back empty, say so.
- Never delete. Archive instead.
- If a sweep fails, report the gap instead of leaving it out silently.
