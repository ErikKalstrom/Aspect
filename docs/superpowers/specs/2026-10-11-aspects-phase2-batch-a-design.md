# Aspects Plugin — Phase 2 Batch A (Frost, Wind) Design

Date: 2026-10-11

## Context

Phase 1 shipped the full architecture (config, persistence, Aspect/Ability framework) plus two working Aspects (Fire, Tide), and was hardened and live-verified against a real Paper 1.21.11 server (see the addenda in
`docs/superpowers/plans/2026-10-11-aspects-phase1.md`). That verification pass also fixed a real defect in the
ability activation trigger: it is now **sneak + swap-hands (the F key)** with an empty main hand, not
sneak + right-click — right-click-on-open-air never fires `PlayerInteractEvent` on Paper, which would have
made every Phase 2 ability unusable in open space. This document assumes that trigger and treats it as settled.

Phase 2 covers the remaining six Aspects (Frost, Thunder, Void, Earth, Wind, Blood) from the original functional
spec. The six vary widely in implementation complexity, so Phase 2 is split into two batches:

- **Batch A (this document, this plan's immediate scope):** Frost and Wind. Both reuse Phase 1's exact patterns
  almost unchanged.
- **Batch B (designed later, after Batch A ships):** Thunder, Earth, Void, Blood. Each needs one genuinely new
  mechanic (velocity dash with safety limits, a single-active-buff guard, safe-teleport destination search, and a
  temporary damage buff with a health-sacrifice cost that must survive death cleanly). Batch B's design will be
  written as its own spec once Batch A is done and reviewed, following the same brainstorm → spec → plan cycle.

## What's new for Batch A (and a correction from the earlier discussion)

Earlier in this project's brainstorming, Frost was described as needing "no new infrastructure." That undersold
one detail: Frost's passive ("Frozen Heart" — immunity to powder-snow freezing damage specifically, without
immunity to unrelated damage) has no vanilla potion-effect equivalent the way Fire's Fire Resistance and Tide's
Water Breathing did in Phase 1. Minecraft has no "freeze immunity" potion effect; the only way to implement this
is to listen for `EntityDamageEvent` with `DamageCause.FREEZE` and cancel it when the damaged player's current
Aspect has the passive enabled.

So Batch A adds exactly one new, small piece:

- **`PlayerDamageListener`** (new, package `listener/`) — one `@EventHandler` method on `EntityDamageEvent`:
  if the event's cause is `FREEZE` and the damaged entity is a player whose current Aspect has
  `passive.freeze-immunity` enabled, cancel the event. Nothing else. This class is intentionally named generically
  (matching the original functional spec's suggested file structure) because Batch B's Blood Aspect will extend it
  with a second handler for outgoing melee damage bonuses — that extension is out of scope for this document and
  will be designed in the Batch B spec.

Everything else needed for Frost and Wind — `Ability`/`PassiveAbility` implementations, `SoundUtil`/`ParticleUtil`
for cosmetics, config-driven numeric values via `AspectDefinition.getDouble/getInt/getBoolean`, the existing
`AbilityManager`/`AbilityCooldownManager` orchestration — is unchanged from Phase 1 and reused exactly as-is.

## Frost

- Rarity: Rare, weight 20 (same tier as Fire).
- **Passive — Frozen Heart:** cancels `FREEZE` damage via the new `PlayerDamageListener`, gated by
  `passive.freeze-immunity` (default `true`). No `onEquip`/`onUnequip` state needed — the listener checks the
  player's *current* Aspect live on every `FREEZE` damage event, so there is nothing to apply or remove when the
  Aspect changes (unlike Fire/Tide's potion-effect passives, there's no lingering state to clean up on unequip,
  death, or disable — the check is purely "is Frost currently equipped and enabled").
- **Active — Frost Nova:** instant (no wind-up, unlike Fire), circular radius 5 blocks, applies Slowness II
  (amplifier 1) for 3 seconds (60 ticks) to enemy players in range, excludes the caster — same
  `getNearbyEntities` + `distance(origin) <= radius` + self-exclusion pattern as `FireActiveAbility`. A
  config-gated optional brief Mining Fatigue application (disabled by default, per the functional spec) uses the
  same per-aspect config-key pattern as everything else (`active.mining-fatigue-enabled`,
  `active.mining-fatigue-ticks`). Cooldown 24 seconds.
- Config keys (`aspects.yml`): `passive.freeze-immunity` (bool, default true); `active.cooldown-seconds` (24),
  `active.radius` (5.0), `active.slowness-duration-ticks` (60), `active.slowness-amplifier` (1),
  `active.mining-fatigue-enabled` (false), `active.mining-fatigue-ticks` (40).

## Wind

- Rarity: Common, weight 40 (same tier as Tide).
- **Passive — Lightfoot:** Speed I applied once on equip, removed on unequip — identical pattern to
  `FirePassiveAbility`/`TidePassiveAbility` (a single `PotionEffect` with `INFINITE_DURATION`), gated by
  `passive.speed-boost` (default true).
- **Active — Wind Leap:** a single velocity impulse, upward + a smaller forward component along the player's look
  direction, magnitude capped so it reads as "a strong jump," not flight. No wind-up (instant, unlike Fire).
  Cooldown 16 seconds. No fall-damage negation by default (per the functional spec — that stays an explicit
  future config option, not default behavior, and is out of scope for Batch A since it isn't default-on).
- Config keys: `passive.speed-boost` (bool, default true); `active.cooldown-seconds` (16),
  `active.upward-velocity` (1.0), `active.forward-velocity` (0.6).

## aspects.yml changes

Both `frost` and `wind` flip from `enabled: false` (Phase 1 stub, `weight: 0`) to `enabled: true` with the real
settings above. `thunder`, `void`, `earth`, `blood` remain disabled stubs, unchanged, with their "not yet
implemented" description text preserved exactly as Phase 1 left it.

## AspectsPlugin wiring

`buildRegistry` gains two more `if (definition != null) { registry.register(...) }` blocks for `frost` and `wind`,
following the exact pattern already used for `fire` and `tide`. The "defined but not yet implemented, skipping
registration" log line naturally stops firing for frost/wind and keeps firing for the remaining four.

## Testing approach

Same split as Phase 1: `PlayerDamageListener`, `FrostActiveAbility`, `FrostPassiveAbility`, `WindActiveAbility`,
`WindPassiveAbility` are all Bukkit-live-entity code with no pure logic to unit test, matching
`FireActiveAbility`/`TideActiveAbility`'s precedent — build-verified only, with a manual in-game checklist
extension (Frost Nova slows enemies without affecting the caster; freeze immunity blocks powder-snow damage
specifically while other damage still applies; Wind Leap gives a capped jump with no flight). Given Phase 1's
Docker-based live-server verification proved itself useful and caught a real bug, the same approach (a disposable
Paper 1.21.11 container, driven by a scripted test client for join/trigger/cooldown checks, plus manual review of
server logs) should be used again for Batch A's checklist rather than leaving it fully to the user, time
permitting.

## What's deliberately out of scope here

Thunder, Earth, Void, and Blood — these become Batch B's own spec once Batch A ships. No crafting/reroller/GUI
work (still Phase 3). No WorldGuard integration (still Phase 5). No change to the existing admin command set or
permissions (frost/wind need nothing new there — `/aspects admin set <player> frost` already works today once the
aspect is registered, since `AdminCommand`/`AspectsTabCompleter` are id-driven, not hardcoded per aspect).
