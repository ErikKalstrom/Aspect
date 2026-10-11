# Aspects SMP Plugin — Architecture & Phasing Design

Date: 2026-10-11

## Context

The full functional specification for the Aspects plugin (8 aspects, GUIs, admin
commands, crafting, persistence, protections, config, tests, docs) is large enough
that it cannot be safely designed, implemented, and verified as a single unit. This
document covers the overall architecture that spans the whole project, plus the
scope of Phase 1 — the first vertical slice we build and verify before expanding to
the rest of the spec.

The full functional requirements (all 8 aspects' exact numbers, commands,
permissions, message copy, GUI layouts, protection rules, etc.) live in the
original project prompt and are treated as the source of truth for *what* each
later phase must implement. This document is the source of truth for *how* the
system is structured and *in what order* it gets built.

## Target environment

- Minecraft: 1.21.11 (confirmed live by the user against the actual Minecraft
  client).
- Server software: Paper, API artifact `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`,
  scope `provided`. Confirmed via PaperMC's own docs/news as a real, current
  (Aug 2026) release — not a guess.
- Java: 21 (confirmed as 1.21.11's required version via PaperMC docs/forums).
- Build: Maven, repository `https://repo.papermc.io/repository/maven-public/`.
- Plugin id/package: `com.aspects.smp`, main class `com.aspects.smp.AspectsPlugin`.

## Package structure

```
com.aspects.smp
├── AspectsPlugin                    bootstrap only: wires managers, registers listeners/commands
├── aspect/   Aspect, AspectRegistry, AspectRarity, AspectManager, AspectAssignmentService
├── ability/  Ability, AbilityManager, AbilityCooldownManager, AbilityContext, ActivationListener,
             impl/ (one class per aspect's active ability, one per passive)
├── item/     RerollerItem, AspectSelectorItem, CustomItemManager
├── gui/      MainAspectGUI, AspectBrowserGUI, AspectDetailsGUI, AdminGUI, RerollConfirmationGUI, GUIListener
├── listener/ PlayerJoinListener, PlayerDeathListener, PlayerRespawnListener, PlayerDamageListener, InventoryListener
├── data/     PlayerAspectData, PlayerDataRepository (interface), SQLitePlayerDataRepository
├── command/  AspectsCommand, AdminCommand, AspectsTabCompleter
├── recipe/   RerollerRecipeManager
├── integration/ WorldGuardIntegration (soft dependency, no hard dep on WorldGuard)
├── config/   ConfigManager
└── util/     MessageUtil, ParticleUtil, SoundUtil
```

Boundary rule: `ability/impl/*` classes depend only on `AbilityContext` — never
reach into GUI, command, or data-layer classes directly. This is what lets a 9th
aspect be added later without touching unrelated systems.

## Data layer

`PlayerDataRepository` is an interface with a single implementation,
`SQLitePlayerDataRepository` (SQLite-only, no YAML toggle — simpler single code
path per explicit decision).

```sql
CREATE TABLE players (
  uuid TEXT PRIMARY KEY,
  aspect_id TEXT NOT NULL,
  assigned_at INTEGER NOT NULL
);
CREATE TABLE cooldowns (
  uuid TEXT NOT NULL,
  ability_id TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  PRIMARY KEY (uuid, ability_id)
);
```

- `org.xerial:sqlite-jdbc`, shaded into the plugin jar via maven-shade-plugin.
- All reads/writes run off the main thread (Paper async scheduler). A player's
  aspect loads early in the join sequence and is cached in memory
  (`Map<UUID, PlayerAspectData>`) for the session; writes are async with errors
  logged, plus a synchronous flush-on-disable so shutdown never loses data.
- Cooldown expiry timestamps are persisted so cooldowns survive restarts.

## Config system

`ConfigManager` owns four files: `config.yml`, `aspects.yml`, `recipes.yml`,
`messages.yml`.

Reload sequence (startup and `/aspects admin reload`):
1. Parse into a staging object.
2. Validate everything (rarity weights ≥ 0, cooldowns ≥ 0, known materials,
   recipe pattern shape, etc.), collecting *all* errors rather than stopping at
   the first.
3. On failure: keep the previously-loaded live config, log every error, abort.
4. On success: atomically swap the live config reference.

Reload never duplicates listeners (registered exactly once in `onEnable`) or
recipes (old recipe keys are unregistered before new ones are registered).

## Ability framework

```java
interface Ability {
  String id();
  boolean activate(AbilityContext ctx);   // false => no cooldown started
}
interface PassiveAbility {
  void onEquip(Player p, AspectConfig cfg);
  void onUnequip(Player p);
}
```

- Passives apply once on equip/join, not reapplied every tick. Potion-effect
  passives refresh only in response to `EntityPotionEffectEvent` if something
  else strips them (configurable), never via a per-tick scan of all players.
- `AbilityCooldownManager` is one `Map<UUID, Map<String, Long>>` of expiry
  timestamps, backed by the `cooldowns` table — no per-player repeating tasks.
- `ActivationListener` handles the default sneak + right-click-empty-hand
  trigger on `PlayerInteractEvent`, debounced per-tick per-player to collapse
  Bukkit's double-fire per physical click. Bails out early for: open inventory,
  dead/spectator, wrong hand/item, disallowed world/region.
- `AbilityContext` bundles player, `Aspect`, `AspectConfig` values, the cooldown
  manager, and a `ProtectionGate` (world/region/PvP checks) so ability impls stay
  pure gameplay logic.

## Phasing

The project is decomposed into sequential phases, each with its own
plan → implement → verify cycle:

1. **Phase 1 (this spec's immediate scope)** — scaffold, config system,
   persistence, aspect registry/assignment, ability framework, Fire + Tide fully
   implemented, minimal commands for manual verification.
2. **Phase 2** — remaining 6 aspects (Frost, Thunder, Void, Earth, Wind, Blood).
3. **Phase 3** — Aspect Reroller item, crafting recipe, all GUIs (main, browser,
   details, reroll confirmation).
4. **Phase 4** — full admin command suite, tab completion, full permission
   matrix, admin GUI.
5. **Phase 5** — WorldGuard soft integration, remaining protection rules,
   polish, full test suite, README and docs.

### Phase 1 scope in detail

Chosen aspects exercise distinct ability patterns:
- **Fire** — damage-immunity-style passive (fire/lava negation) + AOE damage
  active (nearby-entity scan, self-exclusion, burn effect, PvP/region gating).
- **Tide** — simple potion-effect passive (Water Breathing) + self-only heal
  active (max-health clamped).

Deliverables:
- Maven scaffold (`pom.xml` pinned to `paper-api:1.21.11-R0.1-SNAPSHOT`, Java 21,
  shade plugin for `sqlite-jdbc`), `plugin.yml`, `AspectsPlugin` bootstrap.
- `config/ConfigManager` + all four config files with real defaults for Fire and
  Tide. The other six aspects get minimal/disabled stub entries so the file
  shape is right, explicitly commented as not-yet-implemented — never silently
  claimed as working.
- `data/` SQLite repository, async load/save, schema creation on first run.
- `aspect/` registry, rarity-weighted random assignment, first-join logic,
  reroll-exclusion logic. Reroll mechanics are wired and testable via a
  temporary admin command; the Reroller *item* (GUI/crafting) is Phase 3.
- `ability/` framework + Fire and Tide fully implemented per the functional
  spec's exact numbers (radius, damage, cooldowns, etc).
- `listener/` join/respawn/death/quit handling per the "deaths/respawns/relogging"
  requirements (no reroll on death/disconnect/world-change, cooldowns survive
  relogging, aspect persists across restarts).
- Minimal `/aspects info` and `/aspects admin set|reroll` commands, just enough
  to manually verify end-to-end in-game. Full command suite, tab completion,
  GUIs, and permission matrix are later phases.
- Unit tests: rarity-weighted selection math, cooldown gating, reroll-exclusion
  logic, config validation rejecting bad values.

Explicitly **not** in Phase 1: the other 6 aspects' abilities, Reroller item,
crafting, any GUI, admin GUI, full permission set, WorldGuard integration, tab
completion. These will be marked as not-yet-implemented in config comments and
console/README notes, not hidden or claimed as done.

## Error handling

- Config validation errors and SQLite failures log clearly to console with the
  specific bad key/value — no silent fallback that masks a server-owner typo.
- Ability activation failures (cooldown, no permission, unsafe destination,
  etc.) produce a configurable action-bar message and never throw past the
  event listener — every event handler has a top-level try/catch that logs and
  never crashes the plugin.

## Testing approach

- JUnit unit tests for pure logic (rarity math, cooldown math, config
  validation) run via `mvn test`.
- A manual in-game verification checklist is provided per phase for anything
  that needs a live Paper server (ability visuals, actual PvP damage, teleport
  safety).
- Claims are always labeled as "compiled/unit-tested" vs. "needs you to verify
  on a server" — never conflated, per the functional spec's testing
  requirements.
