# Aspects Plugin — Phase 2 Batch B1 (Earth, Thunder) Design

Date: 2026-10-11

## Context

Phase 1 and Phase 2 Batch A shipped and live-verified four Aspects (Fire, Tide, Frost, Wind) on the existing
`Ability`/`PassiveAbility`/`AbilityManager`/`AbilityCooldownManager` framework. Batch A's final whole-branch review
also found and fixed a real bug (freeze immunity leaking to non-Frost players) and flagged that
`AspectsPlugin.buildRegistry`'s per-aspect `if` blocks plus a growing exclusion-list `&&` chain should become a
factory-table before more aspects are added — this document addresses that first.

The remaining four Aspects (Thunder, Void, Earth, Blood) vary widely in complexity, so this phase is split further:

- **Batch B1 (this document):** Earth and Thunder, plus the `buildRegistry` refactor. Both reuse existing patterns
  almost entirely; Thunder is the only one needing a genuinely new mechanic (a wall-safety check for its dash).
- **Batch B2 (designed later):** Void (safe-teleport destination search) and Blood (a temporary damage buff with
  a health-sacrifice cost, melee-damage modification, and death cleanup — the most complex Aspect in the plugin).
  Blood is where `TemporaryEffectManager` actually earns its place; see "What's deliberately not built here" below.

## `buildRegistry` refactor

Today, `AspectsPlugin.buildRegistry` has one copy-pasted `if (definition != null) { registry.register(...) }`
block per Aspect, plus a hand-maintained `!definition.id().equals("fire") && !definition.id().equals("tide") && ...`
chain for the "not yet implemented" log line. At four Aspects this was already flagged as a maintenance risk by
Batch A's final review; adding Earth and Thunder (six total) without fixing it first makes the risk worse, not
better.

Replace it with two factory tables keyed by Aspect id:

```java
private static final Map<String, Supplier<PassiveAbility>> PASSIVE_FACTORIES = Map.of(
        "fire", FirePassiveAbility::new,
        "tide", TidePassiveAbility::new,
        "frost", FrostPassiveAbility::new,
        "wind", WindPassiveAbility::new,
        "earth", EarthPassiveAbility::new,
        "thunder", ThunderPassiveAbility::new
);
private static final Map<String, Supplier<Ability>> ACTIVE_FACTORIES = Map.of(
        "fire", FireActiveAbility::new,
        "tide", TideActiveAbility::new,
        "frost", FrostActiveAbility::new,
        "wind", WindActiveAbility::new,
        "earth", EarthActiveAbility::new,
        "thunder", ThunderActiveAbility::new
);
```

`buildRegistry` loops over `definitions.values()`, and for each one whose id exists in both maps, constructs and
registers an `Aspect`; for any id missing from either map, it logs the existing "not yet implemented" message.
Adding a seventh and eighth Aspect (Void, Blood) in Batch B2 becomes a one-line addition to each map — no `if`
blocks, no exclusion list to remember to update in two places.

## `DirectionUtil` (new shared utility)

Thunder's dash needs the exact same "safe unit horizontal vector from yaw" math that `WindActiveAbility` already
has (`horizontalFromYaw`, added and unit-tested in Batch A specifically because `Location.getDirection()` combined
with flattening-then-normalizing can produce a zero-length vector and crash when a player looks straight up or
down). Rather than have `ThunderActiveAbility` depend on `WindActiveAbility` directly — one ability class
reaching into another's implementation — this method moves into a new `com.aspects.smp.util.DirectionUtil` with
one static method:

```java
public static Vector horizontalFromYaw(float yawDegrees) {
    double yawRadians = Math.toRadians(yawDegrees);
    return new Vector(-Math.sin(yawRadians), 0, Math.cos(yawRadians));
}
```

`WindActiveAbility` calls `DirectionUtil.horizontalFromYaw(...)` instead of its own now-removed static method.
`WindActiveAbilityTest`'s five existing test cases move to a new `DirectionUtilTest` unchanged in substance (same
yaw values, same expected vectors, same unit-length sweep) — this is a pure relocation, not new test logic.

## Earth

- Rarity: Rare, weight 20 (same tier as Fire/Frost).
- **Passive — Stone Skin:** Resistance I applied once on equip, removed on unequip — identical pattern to every
  other passive in the plugin (a single `PotionEffect` with `INFINITE_DURATION`), gated by
  `passive.resistance-boost` (default true).
- **Active — Earthguard:** instant (no wind-up, no AOE — self-only), applies Resistance II (amplifier 1) to the
  caster for 3 seconds (60 ticks). Cooldown 30 seconds. Because the cooldown (30s) is longer than the buff
  duration (3s), the cooldown system alone already guarantees only one Earthguard can be active at a time — no
  new infrastructure needed to enforce the functional spec's "allowing only one Earthguard activation at a time."
  This is the simplest active ability in the plugin: read config values, apply one potion effect to self, play
  sound/particle, return true.
- Config keys (`aspects.yml`): `passive.resistance-boost` (bool, default true); `active.cooldown-seconds` (30),
  `active.resistance-duration-ticks` (60), `active.resistance-amplifier` (1).

## Thunder

- Rarity: Epic, weight 10 (matching the functional spec's Epic tier; the first Epic-rarity Aspect implemented so
  far — Fire/Frost/Earth are Rare, Tide/Wind are Common).
- **Passive — Charged:** Speed I applied once on equip, removed on unequip — identical pattern, gated by
  `passive.speed-boost` (default true). (Both Wind and Thunder grant Speed I; this is correct per the functional
  spec — a player only ever has one Aspect equipped at a time, so there's no stacking concern between them.)
- **Active — Lightning Dash:**
  1. Compute the horizontal launch direction via `DirectionUtil.horizontalFromYaw(player.getLocation().getYaw())`
     — yaw only, not the player's pitch, so the dash always moves across the ground plane regardless of where the
     camera is aimed (matching the spec's "approximately 6-8 blocks on open ground" framing; a pitch-based dash
     would mean looking down cancels most of your horizontal distance, which doesn't match that intent).
  2. Ray-trace forward from the player's eye location along that direction, up to `active.max-distance` (default
     7.0 blocks), using `World.rayTraceBlocks(Location, Vector, double)`. If it returns a hit, clamp the
     effective travel distance to the hit distance minus a small safety margin (`active.wall-safety-margin`,
     default 0.5 blocks) rather than the full configured distance. If nothing is hit, use the full distance.
  3. Scale the velocity magnitude proportionally to the clamped effective distance via a calibration constant
     (`active.velocity-per-block`, default 0.6 — an empirical constant, tunable by server owners since exact
     "how far will velocity X actually carry a player" depends on tick-rate, drag, and terrain, and isn't a fixed
     formula), plus a small fixed upward component (`active.vertical-boost`, default 0.15) to avoid snagging on a
     single-block ledge. This means a wall detected close to the player produces a small, mostly-harmless nudge
     rather than a long, unsafe-magnitude impulse toward a wall — a real (if not mathematically airtight in every
     edge case) mitigation of the tunneling risk the functional spec explicitly warns against, consistent with
     what a single-raycast, single-impulse ability can reasonably guarantee without becoming a
     multi-tick stepped-teleport implementation.
  4. No real lightning is created (per the functional spec). Cooldown 18 seconds.
- Config keys: `passive.speed-boost` (bool, default true); `active.cooldown-seconds` (18), `active.max-distance`
  (7.0), `active.velocity-per-block` (0.6), `active.vertical-boost` (0.15), `active.wall-safety-margin` (0.5).

## aspects.yml changes

`earth` and `thunder` flip from `enabled: false` stubs to `enabled: true` with the settings above. `void` and
`blood` remain disabled stubs, unchanged, exactly as before.

## AspectsPlugin wiring

With the factory-table refactor in place, adding Earth and Thunder is two more entries in each `Map.of(...)` call
— no new `if` blocks, no exclusion-list edits.

## Testing approach

`DirectionUtilTest` (relocated from `WindActiveAbilityTest`, same five cases) remains fully unit-tested — pure
math, no Bukkit dependency. `EarthPassiveAbility`, `EarthActiveAbility`, `ThunderPassiveAbility` are build-verified
only (live-entity mutation, same precedent as every other ability implementation). `ThunderActiveAbility`'s
raycast-and-clamp logic is the one piece of new logic in this batch worth scrutinizing in review for a pure,
testable extraction opportunity (e.g., "given a hit distance or none, and a max distance and margin, what is the
effective distance" is arguably pure and testable without a live `World`) — left as a decision for the
implementation plan rather than specified here, since it depends on exactly how the raycast result threads through
the method.

## What's deliberately not built here

`TemporaryEffectManager` is NOT built in this batch. Earth doesn't need it (see above), and Thunder doesn't need
it either. It will be introduced in Batch B2 specifically for Blood, which is the first Aspect whose damage-event
listener needs to query "is a temporary buff currently active" independent of the cooldown system. Void and Blood
themselves, the Reroller item/crafting/GUIs (Phase 3), the full admin command/permission suite (Phase 4), and
WorldGuard integration (Phase 5) are all out of scope here.
