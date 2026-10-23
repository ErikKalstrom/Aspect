# Aspects Plugin — Progression & Mastery Design Interview

**Status: implemented without product input (2026-10-23).** This interview was offered to the
product team, who declined to answer it (they didn't find the questions relevant to their review).
The feature was still worth shipping rather than leaving unbuilt, so it was implemented with the
lowest-risk, most conservative choice for every open question below: **disabled by default**
(`mastery.yml`'s `enabled: false`), **purely cosmetic rewards only** (a title shown in `/aspects
info` and the main GUI — no cooldown reduction or other gameplay-affecting bonus, per the spec's own
caution against that), **one combined system** (activation count directly drives levels, no separate
stats-only tracker), **counted by successful activations, not time**, **per-(player, Aspect)
progress that survives rerolling away and back** (reset only by an explicit admin command), and
**the same level thresholds for every Aspect** regardless of its cooldown. See the "Aspect Mastery"
section of `README.md` for the shipped behavior, and `mastery.yml` to change the numbers — nothing
here required a code change to retune. If product ever does want to weigh in, these are still the
right questions to ask; the answers below were never given, only assumed.

---

**Purpose of this document:** The product team's re-spec (the "master prompt") asks for an optional
Aspect progression/mastery/statistics system (sections 14.1 and 14.2) as the one genuinely new
feature in an otherwise-already-shipped plugin. The spec deliberately leaves almost every concrete
detail open — "a small number of levels or mastery milestones," example rewards without requiring
all of them, "optionally track ability activations." Someone on the product side needs to make these
calls before implementation starts, because they're gameplay/design decisions, not engineering ones.

**Instructions for whoever runs this (e.g. a Claude Code session helping the product team answer
it):** Conduct this as an interview, not a form dump. Ask ONE question at a time, in order within
each section. Prefer multiple-choice framing where one is offered, but let the person answer freely.
After each answer, briefly confirm you understood it before moving on. Skip a section entirely if the
person says it doesn't matter to them / they trust the implementer's judgment — write down "no
opinion, implementer's judgment stands" rather than forcing an answer. At the end, produce a plain
summary of all answers in the same order as the questions, as a single markdown file, suitable for
sending back to the development session to use as the design brief for implementation.

**Relevant spec text, verbatim (for context — do not just re-ask these as questions, use them to
frame the real questions below):**

> **14.1 Aspect progression** (optional, enabled by default only if balanced)
> - Provide an opt-in progression system with a small number of levels or mastery milestones.
> - Progression must not make an Aspect overwhelmingly strong or create unavoidable power gaps.
> - Example rewards: cosmetic titles, subtle particle variants, GUI badges, or small carefully
>   capped cooldown adjustments.
> - Never award progression for easily farmed self-damage, repeated event exploits, or non-player
>   damage unless configured intentionally.
> - Store progression by UUID and include migration/versioning.
> - Provide configurable progression requirements and rewards.
> - Include an admin reset/inspect operation if progression is implemented.
>
> **14.2 Aspect mastery and statistics**
> - Optionally track ability activations and meaningful milestones.
> - Avoid tracking unnecessary personal information.
> - Keep data storage bounded and efficient.
> - Provide a player-facing mastery page if progression is enabled.

**Current state:** nothing from 14.1/14.2 exists in the plugin yet — the core game (all 8 Aspects,
rerolling, admin tools, WorldGuard integration) is fully shipped and already live-tested. This is
the one new system being built on top of it.

---

## 1. Scope: is this one system or two?

1. The spec describes "progression" (14.1 — levels, rewards) and "mastery and statistics" (14.2 —
   tracking activations/milestones) as two separate subsections, but they clearly depend on each
   other ("provide a player-facing mastery page *if progression is enabled*"). Should these be built
   as **one combined system** (tracking activations IS the progression metric, levels ARE the
   milestones), or as **two separate systems** — a lightweight always-on stats tracker (just numbers,
   no rewards) plus a separate optional progression/leveling layer that reads from it? If you have no
   opinion, the recommendation is to build them as one combined system — it's simpler and the spec's
   own cross-reference implies they're meant to work together.

## 2. What drives progression

2. What should actually count toward a player's progress with an Aspect? Options, pick one or
   describe a combination:
   - **A.** Successful activations of that Aspect's *active* ability only (e.g. every time Fire's
     Inferno Burst actually fires) — simplest, matches the spec's own example most directly.
   - **B.** Successful activations of the active ability, but only when it actually affects another
     player (e.g. Inferno Burst only counts if it hits someone) — rewards actual combat use, not
     just spamming the ability into empty air.
   - **C.** A broader mix: ability activations plus other milestones (time played with this Aspect,
     players defeated while it's equipped, etc).
   - **D.** Something else — describe it.
3. The spec explicitly warns against counting "easily farmed self-damage, repeated event exploits, or
   non-player damage unless configured intentionally." Concretely for this plugin: Blood Pact costs
   the caster 2 HP to activate — should that self-damage ever be countable toward anything (e.g.
   "times sacrificed")? Default assumption if you have no opinion: no, only count the things listed
   in question 2.

## 3. Levels and thresholds

4. How many levels/milestones should there be per Aspect? The spec says "a small number" — is a
   specific number acceptable (e.g. 5), or do you want this to be a configurable list of thresholds
   per Aspect so an admin can add more later without a code change?
5. Should every Aspect use the same activation-count thresholds (e.g. level 2 at 50 activations,
   level 3 at 200, …), or should thresholds be allowed to differ per Aspect (since some Aspects have
   much shorter cooldowns than others and would naturally rack up activations faster — e.g. Wind's
   16s cooldown vs Tide's 35s)?

## 4. Rewards

6. Which of the spec's example reward types do you actually want, for a first version? (multi-select
   — pick as many as you want, or describe something else entirely)
   - Cosmetic title (shown where — player's name in chat, a GUI badge, both?)
   - Subtle particle variant on that Aspect's active ability (e.g. a color/intensity change at higher
     mastery)
   - GUI badge/icon shown next to the Aspect in the player's own menu
   - A small, capped cooldown reduction (if yes: what's the maximum total reduction allowed, as a
     percentage or flat seconds, so it can never make an Aspect "overwhelmingly strong" per the
     spec's own constraint?)
7. Should rewards be purely cosmetic for a first version (title/particle/badge only, no cooldown
   change), with cooldown reduction deferred to a later balance pass once there's real player data?
   This is the lower-risk option if you're unsure.

## 5. Per-Aspect identity and rerolling

8. Is mastery tracked **per Aspect a player has ever held**, or **only for their currently-assigned
   Aspect**? Concretely: if a player masters Fire, then rerolls into Frost, do they keep their Fire
   mastery (so rerolling back to Fire later resumes where they left off), or does mastery reset/pause
   with the Aspect itself?
9. If mastery is per-Aspect-ever-held (question 8), should a player be able to see their mastery
   progress for Aspects they're *not* currently assigned (e.g. via `/aspects info` or a dedicated
   command), or only for whichever Aspect they currently have?

## 6. Default behavior and admin control

10. Should progression be **enabled by default** on a fresh install, or **disabled by default**
    (an admin opts in via config)? The spec says "enabled by default only if balanced" — given this
    is a new, unproven system, is off-by-default the safer choice for a first release?
11. What should the admin reset/inspect operation actually do? Options:
    - Reset a specific player's mastery for one Aspect
    - Reset a specific player's mastery for all Aspects
    - View any player's current mastery levels/progress (read-only inspect)
    - All of the above as separate subcommands
12. Is a reload-safe way to change the level thresholds/rewards without restarting the server
    important to you, or is "requires a restart, like `aspects.yml` changes already do" acceptable?

## 7. Presentation

13. Where should a player see their own mastery progress? Options (pick one or combine):
    - A new tab/section in the existing main Aspects GUI
    - A dedicated "mastery" command (e.g. `/aspects mastery`)
    - Folded into the existing `/aspects info` output
14. Should other players be able to see someone else's mastery level (e.g. via `/aspects inspect`),
    or is this private to each player?

## 8. Anything else

15. Is there anything about progression/mastery from the original spec excerpt above that you want
    to simplify, drop, or change now that you've seen how narrow the rest of this interview got? For
    example: do you actually want this feature at all for a first release, given it's the one piece
    of the whole plugin that isn't already built and tested?
16. Anything else about how this should work that wasn't covered above?

---

*Generated to scope the implementation of spec sections 14.1 (Aspect progression) and 14.2 (Aspect
mastery and statistics) from the product team's re-spec, the only genuinely new feature identified
in an otherwise fully-shipped plugin. Repository `ErikKalstrom/Aspect`, branch
`product-respec-2026-10-18`.*
