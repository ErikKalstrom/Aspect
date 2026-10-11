# Aspects Plugin — Phase 5 (WorldGuard Integration) Design

Date: 2026-10-11

## Context

Phases 1-4 are complete: all 8 Aspects, the Reroller item, player-facing GUIs, the full command/permission
suite, the Aspect Selector item, and the Administrative GUI. Phase 5 is the last remaining phase from the
original functional spec. The exact requirements come from section 11, "Compatibility and Protection":

> **World protection**
> - Check world borders and world heights.
> - Check for safe teleport destinations.
> - Respect configured blocked worlds.
> - Support WorldGuard if it is installed, using a soft dependency or optional integration.
> - Do not require WorldGuard for the plugin to work.
> - Avoid hard dependencies on other plugins unless absolutely necessary.
>
> **PvP protection**
> - Respect configurable world and region restrictions.
> - Provide an option to disable offensive abilities in protected regions.

World borders, world heights, safe-teleport-destination checks, and blocked-worlds were already built in
Phases 1-3 (`ProtectionGate`, `VoidActiveAbility.isSafeDestination`). What remains is specifically the
WorldGuard region layer, matching the suggested file structure's `integration.WorldGuardIntegration` class
(section 14 of the spec).

**Scoping decision, made without a separate approval round per this session's standing instruction to keep
working autonomously:** this is a single batch, not split further — it touches one new class plus small,
targeted extensions to three existing files (`ProtectionGate`, `GeneralConfig`/`AspectsConfigParser`,
`VoidActiveAbility`), comparable in size to a single batch from earlier phases.

## Feasibility check performed before writing this doc

WorldGuard's Bukkit artifact is published on EngineHub's Maven repository (`maven.enginehub.org`) and
resolves cleanly as a `provided`-scope Maven dependency (confirmed live: `com.sk89q.worldguard:worldguard-bukkit:7.0.19`,
transitively pulling `worldedit-bukkit:7.4.4`). `provided` scope means the classes are available at compile
time but are **not** bundled into the shaded jar (confirmed: the built jar's size is unchanged after adding
the dependency) — this is what makes it a genuine soft dependency rather than a hard one. The exact APIs this
design needs were confirmed via `javap` against the real jars (not assumed from memory):
`WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()` returns a `RegionQuery`;
`RegionQuery.testState(com.sk89q.worldedit.util.Location, LocalPlayer, StateFlag...)` returns `boolean`;
`WorldGuardPlugin.inst().wrapPlayer(Player)` returns a `LocalPlayer`; `BukkitAdapter.adapt(org.bukkit.Location)`
converts a Bukkit `Location` to WorldEdit's `Location` type; `StateFlag(String name, boolean defaultValue)` is
a public constructor; `FlagRegistry.register(Flag<?>)` throws the unchecked `FlagConflictException` if a flag
with that name is already registered (e.g. by another plugin, or by our own plugin reloading).

**Correction from the final whole-branch review:** an earlier version of this doc claimed custom-flag
registration belongs in a soft-depending plugin's `onEnable()`. That is wrong and was caught by the review,
not assumed correct here. WorldGuard locks its flag registry (`SimpleFlagRegistry.register` starts throwing
`IllegalStateException`) once WorldGuard's own `onEnable()` runs, and `softdepend` guarantees WorldGuard's
`onEnable()` runs before ours — so registering in our `onEnable()` always hits the locked registry. The
registry is still open during `onLoad()`, which Bukkit calls for every plugin before any plugin's `onEnable()`
runs; `softdepend` ordering still guarantees WorldGuard's `Plugin` instance already exists (loaded, though not
yet enabled) by the time our `onLoad()` runs, which is all a presence check needs — `isEnabled()` must NOT be
part of that check here, since nothing is enabled yet at `onLoad()` time. `WorldGuardIntegration` is
constructed in `AspectsPlugin.onLoad()`, not `onEnable()`, for exactly this reason.

**Second correction from the review:** a presence-check guard inside a single class is not sufficient to keep
that class loadable when WorldGuard is absent. The JVM verifier resolves exception-handler types and
checkcast/instanceof targets when a class is linked, regardless of which branches actually execute — a
`catch (FlagConflictException e)` block or a `registry.register(flag)` call (checking a `StateFlag` is
assignable to `Flag`) inside `WorldGuardIntegration` itself would force a `NoClassDefFoundError` at
`new WorldGuardIntegration(...)` even on a server without WorldGuard, even though the constructor's own logic
never calls those branches. All WorldGuard/WorldEdit API references now live in a separate, package-private
`WorldGuardHook` class that `WorldGuardIntegration` only instantiates after confirming presence —
`WorldGuardIntegration` itself contains zero WorldGuard symbols, so it links safely either way.

## Design

### A dedicated, custom flag — not piggy-backing on WorldGuard's built-in PVP flag

**Decision:** register a new WorldGuard region flag, `aspects-abilities` (a `StateFlag`, default `ALLOW`),
rather than reusing WorldGuard's built-in `PVP` flag. Reusing `PVP` was considered and rejected: a region
could have PvP disabled for reasons unrelated to this plugin (e.g. a shopping district), and silently also
blocking every Aspect ability there — including non-damaging active abilities and the passive effects they
layer onto combat — would be a surprising, undocumented side effect for a server admin who never touched this
plugin's settings. A dedicated flag is exactly what the spec asks for: "**provide an option** to disable
offensive abilities in protected regions" — an explicit, opt-in control, documented and named for what it
does (`/rg flag <region> aspects-abilities deny`), independent of whatever a region's PvP posture is for
other reasons.

### Where the check plugs in: `ProtectionGate`, not a new code path

`ProtectionGate.isAllowed(Player)` (Phase 1) is already the single gate `AbilityManager.tryActivate` consults
before letting any active ability fire — it currently only checks blocked-worlds. This is the natural,
minimal-risk integration point: extending it to also consult the WorldGuard flag means every active ability,
present and future, is automatically covered without touching `AbilityManager` or any of the eight ability
implementations. Passive abilities (reapplied on join/respawn via `PlayerJoinListener`/`PlayerRespawnListener`,
not through `tryActivate`) are deliberately **not** gated by this check — the spec specifically says "offensive
abilities," and a passive buff like Earth's Resistance isn't one; gating passives too would be scope creep
beyond what was asked.

### Soft dependency: fails open, never required

`WorldGuardIntegration` determines once, at construction time (called from `AspectsPlugin.onEnable`, after
`plugin.yml`'s `softdepend: [WorldGuard]` guarantees WorldGuard has already loaded first if present), whether
WorldGuard is actually installed and enabled. If not, `isPresent()` is `false` forever for that server session,
and `isAbilityAllowed(...)` always returns `true` — abilities work exactly as before, with zero behavior
change on a server without WorldGuard, satisfying "do not require WorldGuard for the plugin to work." All
WorldGuard/WorldEdit API calls live inside this one class; the class is only ever asked to *query* a location
after `present` is confirmed `true`, so a server without WorldGuard never causes those classes to be touched.

A new `config.yml` key, `world-restrictions.respect-worldguard` (default `true`), lets a server admin who has
WorldGuard installed for unrelated reasons opt out of this specific integration without uninstalling it —
consistent with the project's existing pattern of making every protection behavior configurable
(`world-restrictions.blocked-worlds`, `reset.immediate-reassign`, etc.).

### Teleport-destination safety: extending `VoidActiveAbility`, the only teleporting ability

The spec: "Do not teleport into solid blocks, lava, the void, protected regions, or across blocked walls."
`VoidActiveAbility.isSafeDestination` (Phase 1) already checks world border, height, solid blocks, and lava
for each candidate distance along the player's facing direction. It's the only ability in this codebase that
teleports (confirmed: `grep -l teleport` across `ability/impl/` returns only this file). This design adds one
more condition to that same method: a candidate destination is also rejected if `aspects-abilities` is denied
there, using the same `WorldGuardIntegration.isAbilityAllowed(Location, Player)` the `ProtectionGate` check
uses — not a second, separate WorldGuard concept. `pickSafeDistance`'s existing shrinking-distance loop
already searches for the closest safe spot and cancels the ability entirely if none exists within range (via
its existing `return false` when `safeDistance == 0`), so this one new condition is a drop-in addition to an
already-correct search, not a redesign.

### What this does *not* add

- No new permission nodes, commands, or GUI screens — this is a pure protection-layer feature.
- No `build`/`block-place` flag checks, no chest-protection, no claim-visiting restrictions — the spec's
  "World protection" and "PvP protection" subsections are specifically about abilities and teleportation,
  not general build permissions, and WorldGuard already handles those independently of this plugin.
- No bypass-permission handling of our own: `RegionQuery.testState(Location, LocalPlayer, ...)` already
  honors WorldGuard's own `worldguard.region.bypass.*` permission for the given player, for free, by design
  of the API — reimplementing that would duplicate WorldGuard's own mechanism.

## Testing approach

Following this project's established split: `AspectsConfigParser.parseGeneral`'s new `respect-worldguard`
parsing is pure (no Bukkit API) and gets direct unit tests, exactly like `reset.immediate-reassign` before it.
`WorldGuardIntegration` itself, `ProtectionGate`'s extended `isAllowed`, and `VoidActiveAbility`'s extended
destination check all require a live Bukkit `Player`/`World`/plugin registry and are manual-verification-only,
consistent with every other Bukkit-API-dependent class in this codebase. Live verification requires installing
WorldGuard (and its own WorldEdit dependency) into the `aspects-test` Docker container, defining a test
region, setting the `aspects-abilities` flag to `deny`, and confirming both that ability activation is refused
inside it and that Void's teleport ability search skips over it to land outside, plus a control test
confirming abilities still work normally with WorldGuard either absent or present-but-flag-unset.

## What's deliberately out of scope here

This is the final phase from the original functional spec — after this, net-new scope would be future work
beyond spec coverage, not part of Phase 5.
