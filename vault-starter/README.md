# Starter vault

An empty Obsidian vault with a folder structure, instructions for Claude, a few templates and skills, and a routes file for the phone app. Copy this folder, rename it, and it is yours.

## What is in it

| Path | What it is |
|---|---|
| `00-Inbox/` | Where captures land before you sort them. |
| `10-Projects/` | Things with an end: a trip, a renovation, an application. |
| `20-Areas/` | Things without an end: finances, health, home, family. |
| `30-Resources/` | Reference material by topic. `quotes/` and `recipes/` are started for you. |
| `40-Archive/` | Finished projects and notes you no longer need in view. |
| `50-Daily/` | One note per day. |
| `60-Templates/` | Templates for a daily note, a project, a quote and an evergreen note. |
| `90-Attachments/` | Images and scans. |
| `CLAUDE.md` | The operating manual Claude Code reads when you start it in this folder. |
| `AGENTS.md` | A short brief on you and the folder map. Claude Code reads it, and the phone app sends it with every Ask conversation. |
| `.claude/skills/` | Two skills: a weekly review and an article summarizer. |
| `danebrain-routes.json` | The phone app's filing destinations, matching these folders. |
| `.obsidian/` | Three small settings: attachments go to `90-Attachments/`, new notes to `00-Inbox/`, daily notes to `50-Daily/`. |

The folders follow PARA (Projects, Areas, Resources, Archive). The numbers only fix the order in the sidebar.

## Set it up

1. Copy this folder to where you keep documents and rename it, for example `MyVault`.
2. In Obsidian choose **Open folder as vault** and pick it.
3. Open `AGENTS.md` and `CLAUDE.md` and replace the "fill this in" lines with a few facts about you.
4. In a terminal, `cd` into the vault and run `claude`. Try "what is in my inbox?" or "run my weekly review".

For the phone, see the main README.

## Change it

Nothing here is required. Rename folders, delete the ones you do not use, add your own. If you change folders, update the folder tables in `CLAUDE.md` and `AGENTS.md`, and the `folder` values in `danebrain-routes.json`, so Claude and the phone app keep filing into the right places.
