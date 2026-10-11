# Aspects Plugin — Stakeholder Interview Questions

**Purpose of this document:** Phase 2 (all 8 Aspects — Fire, Tide, Frost, Wind, Earth, Thunder, Void, Blood) is now implemented, tested, code-reviewed, and live-verified on a test server. Several design decisions were made unilaterally during implementation because no stakeholder was available to ask at the time. This document collects those open questions so the people who commissioned the plugin ("beställarna") can answer them. Their answers should be used to update the project's design specs in `docs/superpowers/specs/` and may trigger follow-up implementation work.

**Instructions for whoever runs this (e.g. a Claude session helping the stakeholders answer it):** Conduct this as an interview, not a form dump. Ask ONE question at a time, in order within each section. Prefer multiple-choice framing where one is offered, but let the stakeholder answer freely. After each answer, briefly confirm you understood it before moving on. Skip a section entirely if the stakeholder says it doesn't matter to them / they trust the existing implementation — write down "no opinion, implementer's judgment stands" rather than forcing an answer. At the end, produce a plain summary of all answers in the same order as the questions, suitable for pasting back to the development session to update the specs.

---

## 1. Gameplay balance (numbers an engineer picked without a designer's input)

All cooldowns, percentages, and amounts below were chosen using engineering judgment for "feels reasonable," not playtesting data.

1. Aspect rarity weights currently are: Wind & Frost = COMMON (weight 40 each), Fire, Tide & Earth = RARE (weight 20 each), Thunder & Blood = EPIC (weight 10 each), Void = LEGENDARY (weight 5, rarest). Does this distribution match what you want players to experience, or should specific Aspects be made more/less common?
2. Active-ability cooldowns range from 16s (Wind) to 30s (Earth), clustered mostly around 18–28s. Do any individual Aspects feel like they should be stronger/weaker relative to the others based on these numbers, or is a full balance pass something you want to do yourselves after players have tried it live?
3. Blood's melee bonus (+10% passive, +20% more for 5s when active, 2 HP sacrifice) and Void's fall-damage reduction (50%) and teleport range (7 blocks) — are these magnitude choices fine as shipped defaults, keeping in mind every one of these numbers is a single config value in `aspects.yml` and can be changed without touching code?

## 2. Aspect-specific design decisions

4. **Void Step (teleport) can go through thin walls.** The ability only checks that the *destination* spot is safe (not inside a block, not lava, within world bounds) — it does not check whether anything solid is between the player and that destination. In practice this lets a player standing next to a thin wall "blink" through it (up to 7 blocks). Is this acceptable (a deliberate "blink" feel), or should it be changed to refuse if the path is blocked by any solid block?
5. **Void Step can land you in mid-air.** The safety check only requires the destination's floor block not be lava — it doesn't require solid ground. A player can teleport off a ledge and fall (softened by Void's own 50% fall-damage reduction). Is that acceptable, or should Void Step always require solid footing at the destination?
6. **Blood Pact's health sacrifice now bypasses armor, Resistance, and hit-invulnerability entirely** (it was originally implemented as normal damage, but that let players "buy" the buff for free right after taking any other hit, which a code review caught and we fixed). The fix means the 2-HP cost is now always paid in full, with no way to reduce it via gear. Is a guaranteed, non-reducible cost the right call, or should it behave like normal damage that armor/potions can mitigate?
7. **A Blood player wearing Thorns armor gets the Blood melee bonus applied to their Thorns-reflected damage too**, since the plugin currently treats "any damage event where the damager is a Blood-aspect player" as eligible for the bonus. This is a narrow edge case — do you want Thorns excluded, or is this fine to leave as-is?

## 3. Interaction / controls

8. **Activating an Aspect's active ability requires sneaking (shift) and pressing F (swap-hands) at the same time**, not a simple right-click. This was forced by a Paper API limitation discovered during Phase 1 (plain right-click-into-empty-air doesn't fire a usable server event). Is sneak+swap an acceptable permanent control scheme for players, or do you want a different trigger investigated (e.g. a specific item in hand, a command, a GUI button)?

## 4. Persistence & server model

9. Aspects persist per-player across sessions (SQLite) and are never changed by death — only by an admin command (`/aspects admin set|reroll`) or, for new players, random assignment on first join. Does this match the intended player experience, or should there be a player-facing way to reroll (e.g. an item, a cooldown-gated self-service command, a cost)?
10. Is this plugin intended to run on a single standalone server, or as part of a multi-server network (BungeeCord/Velocity) where a player's Aspect would need to follow them between servers? The current SQLite-per-server design does not sync across servers — this matters a lot for Phase 3+ planning if it's the latter.

## 5. Scope still ahead (Phases 3–5, not yet started)

11. **Phase 3 (Reroller item, crafting recipe, GUIs)** was never specced in detail beyond "exists." What should the Reroller item's crafting recipe be, should it be consumed on use, should it have a cooldown or usage limit, and what should the Aspect-selection GUI actually let a player see/do (just reroll randomly, or preview/pick from a list)?
12. **Phase 4 (full admin command/permission suite)** — right now there are only two admin subcommands (`set`, `reroll`), gated by two permission nodes. What additional admin tooling do you actually want (e.g., bulk reroll, per-Aspect enable/disable toggles at runtime, a player-facing `/aspects info` command, statistics/leaderboards)?
13. **Phase 5 (WorldGuard integration)** — none of the Aspects currently respect WorldGuard regions (e.g., Void could teleport into a protected base, Fire's AOE could damage players in a safe-zone). Do you want this gated behind WorldGuard flags, and if so, do you have specific region/flag naming conventions already in use on your server(s)?

## 6. Anything else

14. Is there any functionality from the original specification that, now that you've seen Phases 1–2 built, you want to reconsider, simplify, or drop entirely?
15. Is there anything about how the plugin currently behaves in-game (if you've tested it yourselves) that surprised you or felt wrong, even if it's not covered by the questions above?

---

*Generated after Phase 2 (all 8 Aspects) was implemented, reviewed, and live-verified. Source: development session on 2026-10-11, repository `ErikKalstrom/Aspect`.*
