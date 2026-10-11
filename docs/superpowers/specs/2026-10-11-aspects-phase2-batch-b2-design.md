# Aspects Plugin — Phase 2 Batch B2 (Void, Blood) Design

Date: 2026-10-11

## Context

Phase 1 and Phase 2 Batches A and B1 shipped and live-verified six Aspects (Fire, Tide, Frost, Wind, Earth,
Thunder). This document covers the final two Aspects from the original functional spec: Void (safe-teleport
destination search) and Blood (a temporary melee-damage buff with a health-sacrifice cost). Both were deferred to
last because they are the two most complex Aspects in the plugin — Void needs a genuinely new "find a safe spot"
search algorithm, and Blood needs new shared infrastructure (`TemporaryEffectManager`), a new listener
(`PlayerDeathListener`), and an extension to the existing `PlayerDamageListener`.

**Scoping decision, made without a separate approval round per this session's standing instruction to keep working
autonomously:** Blood alone is comparable in size to all of Batch B1 combined (two new listener concerns, one new
shared manager, two ability classes, and a precondition check no other Aspect has). Splitting it from Void into its
own implementation plan keeps each plan reviewable at the same granularity as every prior batch. This document
covers the design for both; `docs/superpowers/plans/2026-10-11-aspects-phase2-batch-b2a-void.md` and
`...-batch-b2b-blood.md` will be the two implementation plans, executed in that order.

## Void

- Rarity: Legendary, weight 5 (the rarest Aspect in the plugin, matching the functional spec).
- **Passive — Voidborn:** reduces (not eliminates) fall damage by a configurable percentage, default 50%. Unlike
  Frost's freeze immunity (a full cancel) or every other passive so far (a vanilla potion effect), this needs a
  *partial* damage reduction, which has no vanilla potion-effect equivalent either. This extends
  `PlayerDamageListener` (already built in Batch B1 for Frost's freeze immunity, and already intended to grow a
  second handler for Blood's melee bonus — see below) with a third handler: on `EntityDamageEvent` with cause
  `FALL`, if the damaged player's current Aspect is `"void"` and `passive.fall-damage-reduction` is enabled,
  multiply the remaining damage by `(1 - reductionPercent / 100.0)` via `event.setDamage(...)`, not
  `event.setCancelled(true)`. Checking the Aspect id first (`aspect.id().equals("void")`) before reading any
  config flag is mandatory here — this is exactly the class of bug Batch B1's final review found in the original
  Frost implementation (a `getBoolean(key, defaultTrue)` call reached for any Aspect lacking the key, silently
  granting the behavior to everyone). Every future `PlayerDamageListener` handler must check the Aspect id first.
- **Active — Void Step:** teleports the player forward (horizontal direction only, via the existing
  `DirectionUtil.horizontalFromYaw` — consistent with Wind and Thunder, and because "forward" for a teleport should
  mean "across the ground the player is looking across," not up or down based on camera pitch) up to a configured
  maximum range, searching for the nearest safe landing spot starting from the far end and working inward. A
  candidate point at a given distance is "safe" when: the block at that point and the block above it (the player's
  two-block hitbox) are both non-solid and not lava, the block below them is not lava, the point is inside the
  world border, and the point's Y is within the world's height limits. If no safe point is found anywhere from the
  max distance down to 1 block, the ability fails cleanly — no teleport, no cooldown started, matching every other
  "could not find anything useful to do" ability failure in this plugin.

  The distance-selection logic is extracted as a pure, unit-tested function exactly like Thunder's
  `effectiveDistance` in Batch B1 — `pickSafeDistance(int maxDistance, IntPredicate isSafeAtDistance) -> int`, which
  scans from `maxDistance` down to `1` and returns the first distance for which the predicate is true, or `0` if
  none is. The predicate itself (checking real Bukkit `Block` types, the world border, and height limits) is
  necessarily Bukkit-API-dependent and therefore manual-verification-only, but the *scanning order and
  fail-safe-to-zero behavior* — the part a config mistake or an edge case could get wrong — is fully testable with
  a fake predicate, the same way Thunder's wall-clamp math was.

  Portal particles play at both the departure and arrival locations; a teleport sound plays once. No real
  WorldGuard region check is added — per the project's established phasing, that is Phase 5's job; a server
  without WorldGuard installed must not be affected, and this Aspect doesn't need WorldGuard to be safe by itself
  (the safe-destination search already prevents teleporting into a wall, lava, or outside the world).

- Config keys (`aspects.yml`): `passive.fall-damage-reduction` (bool, default true), `passive.reduction-percent`
  (int, default 50); `active.cooldown-seconds` (22), `active.max-distance` (7.0).

## Blood

- Rarity: Epic, weight 10 (same tier as Thunder).
- **Passive — Bloodlust:** a flat percentage bonus (default 10%) to the player's *outgoing melee damage*, always
  active while Blood is equipped. Like Frost's and Void's passives, this has no vanilla potion-effect equivalent —
  melee damage modification requires listening to `EntityDamageByEntityEvent`. `BloodPassiveAbility` itself is
  therefore a no-op (matching `FrostPassiveAbility`'s precedent exactly) — the actual bonus is enforced by a new
  handler on `PlayerDamageListener`.
- **Active — Blood Pact:** sacrifices 2 health points (1 heart) to grant an *additional* 20% melee damage bonus
  (stacking additively with the passive's 10%, so 30% total while the buff is active — matching the functional
  spec's wording of "increase outgoing melee damage by a configurable amount" on top of the always-on passive) for
  5 seconds. Two things make this different from every prior active ability:
  1. **A health-affordability precondition.** The ability must reject activation (no cooldown, no health lost) if
     the player's current health minus the sacrifice amount would be zero or less — the functional spec is explicit
     that this ability must never be able to kill the caster. This check is a simple comparison
     (`player.getHealth() - sacrificeAmount > 0`), inlined directly in `activate()` rather than extracted into its
     own tested function — it's a single comparison with no branching complexity worth isolating, unlike Thunder's
     multi-branch distance clamp.
  2. **A new timed-buff tracker.** The "is the 20% bonus currently active" question has to be answerable from a
     *different* code path (the melee-damage listener, firing on a later, unrelated event) than the one that
     started the buff. This is exactly the gap Batch B1's design explicitly deferred: a new
     `TemporaryEffectManager`, shaped identically to the existing `AbilityCooldownManager` (a clock-injectable,
     per-player expiry map — see "New shared infrastructure" below), tracks `isActive(playerId, "blood-pact")`.
     Re-activating mid-buff simply extends the same timer rather than stacking a second bonus, because the damage
     listener only ever checks a boolean, never a counter — the functional spec's "must not stack through repeated
     activations" falls out of this naturally rather than needing a separate guard.
- Config keys: `passive.melee-damage-bonus` (bool, default true), `passive.melee-damage-bonus-percent` (int,
  default 10); `active.cooldown-seconds` (28), `active.sacrifice-amount` (2.0), `active.bonus-percent` (int,
  default 20), `active.buff-duration-ticks` (int, default 100 = 5s).

## New shared infrastructure

- **`TemporaryEffectManager`** (new, package `ability/`) — the piece Batch B1 explicitly deferred. Same shape as
  `AbilityCooldownManager`: a `ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>` of expiry timestamps, an
  injectable `LongSupplier` clock for testability, `startBuff(UUID, String buffId, long durationMillis)`,
  `isActive(UUID, String buffId) -> boolean`, and `clear(UUID)`. Unlike `AbilityCooldownManager`, it has **no
  persistence** — buffs are explicitly temporary combat state, not meant to survive a restart or even a relog
  (the functional spec: "temporary ability effects should not persist incorrectly after death"), so it is pure
  in-memory, cleared on death and on quit.
- **`PlayerDeathListener`** (new, package `listener/`) — the piece Phase 1 correctly judged unnecessary (Fire/Tide
  have no caster-side state that death needs to clean up) and Batch B1 still didn't need (Earth/Thunder don't
  either). Blood Pact does: on `PlayerDeathEvent`, call `temporaryEffectManager.clear(player.getUniqueId())`. This
  is also where "dying does not reroll the Aspect" is implicitly satisfied by the simple fact that nothing in this
  listener (or anywhere else) rerolls on death.
- **`PlayerDamageListener` gains a third and fourth handler.** It already has one (`EntityDamageEvent` for Frost's
  freeze immunity, from Batch B1). Void's fall-damage reduction is a second `EntityDamageEvent`-cause check in the
  *same* handler method (both are cause-based checks on the same event type, so they belong together rather than
  as separate `@EventHandler` methods — this does mean the single handler now checks two different causes against
  two different Aspects, each gated by its own `aspect.id().equals(...)` check first). Blood's melee bonus is a new
  `@EventHandler` on `EntityDamageByEntityEvent` (a different event type, so a separate method), checking that the
  damager is a `Player`, looking up their current Aspect, and — again, id-check first — applying the passive and
  (if `temporaryEffectManager.isActive(...)`) active bonuses via `event.setDamage(event.getDamage() * multiplier)`
  exactly once, with no recursive `.damage()` calls (the functional spec's explicit "avoid infinite damage
  amplification from recursive event handling" concern, satisfied the same way Fire's AOE damage already satisfies
  it — only ever adjusting the event's own damage value, never calling `.damage()` again inside the handler).
- **`AspectManager.unload`, `adminSet`, and `adminReroll`** gain one more line each: clearing
  `temporaryEffectManager.clear(player.getUniqueId())` alongside the existing `cooldownManager.clearCache(...)`
  call, so a player who quits, gets admin-reassigned, or rerolls away from Blood doesn't carry a dangling (if
  harmless, since the id-check-first damage listener would ignore it anyway) buff entry.
- **`PlayerQuitListener`** also clears `TemporaryEffectManager` state on quit, for the same "temporary means
  temporary" reasoning — a relog should not resume a Blood Pact buff.

## aspects.yml changes

`void` and `blood` flip from `enabled: false` stubs to `enabled: true` with the settings above. No other Aspects
change. This is the last of the eight functional-spec Aspects — Phase 2 is complete once Batch B2 ships.

## Testing approach

`TemporaryEffectManager` gets the same direct unit-test treatment as `AbilityCooldownManager` (clock-injectable,
no Bukkit dependency, straightforward to construct and assert against). `VoidActiveAbility`'s `pickSafeDistance`
gets the same treatment as Thunder's `effectiveDistance`. Everything else — both passives, both actives' live
Bukkit-API logic, `PlayerDeathListener`, and the `PlayerDamageListener` extensions — is manual-verification-only,
consistent with this project's established split between pure decision logic (tested) and live-entity mutation
(server-verified).

## What's deliberately out of scope here

The Aspect Reroller item, its crafting recipe, and all GUIs remain Phase 3. The full admin command/permission suite
remains Phase 4. WorldGuard integration remains Phase 5. No change to the existing `/aspects` command set is needed
— `AdminCommand`/`AspectsTabCompleter` are already id-driven from `aspects.yml`, not hardcoded per Aspect, so
`/aspects admin set <player> void` and `blood` work the moment these two Aspects are registered.
