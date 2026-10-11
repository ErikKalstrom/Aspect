# Aspects Plugin — Phase 4 (Full Command/Permission Suite, Aspect Selector, Admin GUI) Design

Date: 2026-10-11

## Context

Phases 1-3 are complete: all 8 Aspects, the self-service Reroller item/crafting/confirmation, and the
player-facing informational GUIs. Phase 4 is the last functional-spec phase before Phase 5 (WorldGuard) —
it covers the remaining player commands (`list`, `inspect`, `help`), the remaining administrative commands
(`give-reroller`, `reset`, `give`/`give-selection`, `clear-cooldown`, `reload`, `list`, `debug`), the full
permission node set from the original spec, the Aspect Selector item, and the administrative GUI.

The exact requirements below are pulled directly from the original project prompt (section 7, "Operator and
Staff Commands", and the relevant parts of section 8, "GUI System") — the project's source of truth for
*what* to build.

**Scoping decision, made without a separate approval round per this session's standing instruction to keep
working autonomously:** this phase is split into three batches, continuing the same granularity as every
prior phase's batches:

- **Batch 4a — Player commands and simple admin commands.** `/aspects list`, `/aspects inspect <player>`,
  `/aspects help`, `/aspects admin give-reroller`, `/aspects admin reset`, `/aspects admin clear-cooldown`,
  `/aspects admin reload`, `/aspects admin list`, `/aspects admin debug`, plus the full permission node set
  and tab completion for all of it. No new items, no new GUIs — pure command-layer work reusing existing
  managers (`CustomItemManager`, `AbilityCooldownManager`, `ConfigManager`, `AspectManager`).
- **Batch 4b — Aspect Selector item.** `/aspects admin give <player> <aspect>` (an Aspect Selection Token
  for one specific Aspect) and `/aspects admin give-selection <player>` (a generic Selector that opens a
  picker GUI). This needs one new item type, one new GUI, and two new admin commands — comparable in size
  to Batch 3a's Reroller item work, so it gets its own batch.
- **Batch 4c — Administrative GUI.** `/aspects admin` (no further args) opens a GUI for player selection,
  viewing/assigning Aspects, forcing rerolls, giving items, and clearing cooldowns, with confirmation for
  destructive actions. This is the largest remaining GUI piece and depends on Batch 4a's underlying admin
  operations already existing as callable methods (the GUI is a UI layer over the same operations the text
  commands already perform) and Batch 4b's Selector item existing (the Admin GUI can give one).

Each batch is independently shippable and testable, matching the pattern established in every prior phase.

## Batch 4a — Player commands and simple admin commands

### `/aspects list`

Spec: "Display all enabled Aspects and their descriptions." A plain-text command — this is the text
equivalent of `AspectBrowserGUI` (Phase 3), listing every *enabled* Aspect's display name, rarity (colored,
via the existing `AspectRarity.color()`), and description, one per chat line. No pagination needed at 8
Aspects. Reuses `AspectRegistry.enabled()` (already exposed via `AspectsPlugin.getAspectRegistry()` from
Phase 3).

### `/aspects inspect <player>`

Spec: "Display another player's Aspect if the command user has permission to inspect players." Gated by a
new permission `aspects.inspect` (default `op`, per the spec's permission list). Prints the target's current
Aspect, rarity, and description to the inspecting sender — the same three lines `handleInfo` already prints
for the sender's own Aspect, parameterized by a different player. If the target has no Aspect assigned (not
yet joined, or data not loaded), report that clearly rather than crashing.

### `/aspects help`

Spec: "Display available commands and their usage." A static, hardcoded list of every player-facing command
this plugin has as of Phase 4 (`/aspects`, `/aspects info`, `/aspects list`, `/aspects inspect <player>`,
`/aspects help`), each with a one-line description. Admin commands are omitted from the player-facing help
text (an `/aspects admin help` listing admin subcommands is a reasonable follow-up but not specified — out
of scope here; `/aspects admin` with no args already errors with a usage line listing the two subcommands
that exist today, and Batch 4a extends that usage line to cover every new admin subcommand it adds).

### `/aspects admin give-reroller <player> [amount]`

Spec: "Give a player one or more valid Aspect Rerollers. Default amount: 1. Validate amounts and player
names. If the inventory is full, safely drop the remainder or report that the item could not be delivered.
Log administrative item grants." Reuses `CustomItemManager.createReroller()` (Phase 3 Batch 3a) — this
command's only new logic is argument parsing (default to 1, reject non-positive or absurdly large amounts —
cap at 64, a single stack, since `ItemStack.setAmount` beyond 64 silently gets clamped by Bukkit anyway and
the original spec's own default recipe only ever makes 1 at a time) and delivery: use
`Player#getInventory()#addItem(...)` (which returns any items that didn't fit) and drop the leftover at the
player's feet via `World#dropItem(...)` rather than silently discarding it, satisfying "safely drop the
remainder." Every grant is logged via `plugin.getLogger().info(...)`, satisfying "log administrative item
grants."

### `/aspects admin reset <player>`

Spec: "Reset a player's assigned Aspect and related state. Define whether the reset immediately assigns a
new random Aspect or waits for the next join. Make the behavior configurable and clearly documented."

**Decision:** add a new `config.yml` key `reset.immediate-reassign` (default `true`) under a new top-level
`reset:` section. When `true` (default), `/aspects admin reset <player>` immediately assigns a new random
Aspect to an online target — this is functionally almost identical to `adminReroll`, with one difference:
`reset` does **not** honor `exclude-current-aspect` (a reset should be able to land on the same Aspect again,
since the point of "reset" is "start over," not "guarantee something different" — that distinction is what
"reroll" is for). This is implemented as a third thin wrapper around `AspectManager`'s existing private
`performReroll(Player, boolean excludeCurrent)` helper (Phase 3 Batch 3a): `resetAspect(Player)` calls
`performReroll(player, false)`. When `reset.immediate-reassign` is `false`, the command instead clears the
player's stored Aspect data entirely (deletes their row via a new `PlayerDataRepository.deletePlayer(UUID)`
method) and leaves them with no Aspect until their next join, at which point the existing
`AspectManager.loadOrAssign` first-join path naturally assigns a new random Aspect (since no stored data will
be found) — no new assignment logic needed for this branch, only the deletion.

"Related state" being reset means the same cooldown and temporary-effect clearing `performReroll` and
`unload` already do — both code paths route through code that already clears
`cooldownManager.clearCache(...)` and `temporaryEffectManager.clear(...)`.

### `/aspects admin clear-cooldown <player>`

Spec: "Clear all active ability cooldowns for a player." Directly calls the existing
`AbilityCooldownManager.clearCache(UUID)` (Phase 1) — this command is a thin wrapper with no new manager
logic needed at all.

### `/aspects admin reload`

Spec: "Reload configuration files safely. Validate the configuration before replacing live settings. Do not
erase player data. Avoid duplicate listeners or duplicate recipe registrations." `ConfigManager.loadAll()`
already implements the staged-validate-then-swap pattern this requires (Phase 1) — reload is a matter of
calling it again and reporting success/failure to the command sender, **plus** re-registering the Reroller
recipe (`RerollerRecipeManager.register(configManager.recipes())`, which already does remove-then-add
internally — Phase 3 Batch 3a — so calling it again on reload is already duplicate-safe by construction, no
new code needed there). No listeners are ever re-registered on reload (they're registered exactly once in
`onEnable`, per Phase 1's design), so "avoid duplicate listeners" is already satisfied by the existing
architecture; this command must not call `registerEvents` again for anything.

### `/aspects admin list`

Spec: "List all enabled and disabled Aspects." Iterates `AspectRegistry.all()` (not just `.enabled()`),
printing each Aspect's id and enabled/disabled state.

### `/aspects admin debug <player>`

Spec: "Display useful diagnostic information such as assigned Aspect, cooldown state, and relevant plugin
settings." Prints: the target's assigned Aspect id (or "none"), whether their active ability is currently on
cooldown and for how many more seconds (via `AbilityCooldownManager.remainingMillis`), and a couple of
relevant general settings (`exclude-current-aspect`, `reset.immediate-reassign`) — enough to diagnose "why
isn't this player's Aspect behaving as expected" without needing server file access.

### Permissions (full set, registered in `plugin.yml`)

Per the spec's exact list in section 7, all registered with `op` as default except `aspects.use` (already
`true` by default from Phase 1):

```
aspects.use            (default: true)   — already exists
aspects.gui             (default: true)   — new; gates opening the player GUI (/aspects with no args)
aspects.inspect         (default: op)     — new
aspects.admin           (default: op)     — already exists
aspects.admin.give      (default: op)     — new; give-reroller AND give/give-selection (Batch 4b) share this node,
                                             per the spec listing them together as "give reroller items" /
                                             "give Aspect selection items" under one umbrella permission name
aspects.admin.set       (default: op)     — already exists
aspects.admin.reroll    (default: op)     — already exists
aspects.admin.reset     (default: op)     — new
aspects.admin.cooldown  (default: op)     — new
aspects.admin.reload    (default: op)     — new
aspects.admin.debug     (default: op)     — new
aspects.admin.selector  (default: op)     — new (Batch 4b uses this specifically for give-selection,
                                             distinct from aspects.admin.give per the spec listing both
                                             "aspects.admin.give" and a separate selector concept — see
                                             Batch 4b for the exact split)
```

`aspects.gui` is checked in `AspectsCommand.handleOpenMenu` (Phase 3) **in addition to** the existing
`aspects.use` check it already has — the spec lists them as two separate permissions even though in practice
every player who can use the plugin at all (`aspects.use`) should also be able to open its menu (`aspects.gui`)
under normal default-`true` settings for both; keeping them as two independent checks (not collapsing them
into one) is what lets a server owner revoke menu access for a specific player/group without touching their
ability to use abilities, matching the spec's intent of "do not assume every staff member is an operator" —
i.e., permissions must be independently revocable, never bundled.

### Tab completion

`AspectsTabCompleter` is extended to offer every new subcommand name, and player-name/Aspect-id completion
for every new subcommand that takes those arguments, following the exact pattern already established for
`set`/`reroll`.

## Batch 4b — Aspect Selector item

### Two distinct concepts, exactly as the spec separates them

- **`/aspects admin give <player> <aspect>`** gives an **Aspect Selection Token for one specific, named
  Aspect** — using it immediately assigns that exact Aspect to whoever uses it (with the usual
  `exclude-current-aspect`-style confirmation flow reused from the Reroller's confirmation GUI pattern,
  since assigning an Aspect is just as consequential as rerolling one). The spec's explicit requirement —
  "do not silently assign the Aspect when the command claims to give an item" — means the command itself
  must never touch `AspectManager` directly; it only ever hands over an item, and the *item's use* is what
  performs the assignment, through the same confirm/cancel GUI flow the Reroller already uses.
- **`/aspects admin give-selection <player>`** gives a **generic Aspect Selector** — using it opens a picker
  GUI (new `gui/AspectSelectorGUI`, built on the existing `AspectsGuiHolder`/`InventoryListener`
  infrastructure, visually similar to `AspectBrowserGUI` but clicking an entry *does* assign that Aspect,
  unlike the read-only Browser) letting the holder choose which Aspect to become.

### Item identification

Both items are `item/AspectSelectorItem` variants, following `CustomItemManager`'s exact established pattern
(Phase 3): a `PersistentDataContainer`-tagged item, never identified by name/lore. Two separate PDC keys:
`aspects:selector-specific` (value: the target Aspect's id, as a `STRING` PDC type) for the named-Aspect
token, and `aspects:selector-generic` (a `BOOLEAN` flag) for the open-a-picker-GUI item. `CustomItemManager`
gains `createSelectorToken(String aspectId) -> ItemStack`, `createGenericSelector() -> ItemStack`,
`getSelectorTokenAspectId(ItemStack) -> Optional<String>`, and `isGenericSelector(ItemStack) -> boolean`.

### Permission split

Per the spec's permission list including both `aspects.admin.give` (used by `give-reroller` in Batch 4a) and
a distinct selector concept: `/aspects admin give <player> <aspect>` is gated by `aspects.admin.give` (giving
a specific-Aspect token is conceptually the same "give an item" operation as `give-reroller`), while
`/aspects admin give-selection <player>` is gated by the more specific `aspects.admin.selector` — matching
the spec's explicit "make the selector permission-restricted or admin-only by default," which calls out the
generic, player-choice-granting Selector as needing its own, separately revocable permission distinct from
the simpler "hand someone a fixed item" operations.

### Using the items

A new `item/AspectSelectorInteractListener` (mirroring `RerollerInteractListener` exactly) handles
right-click for both item types:
- A specific-Aspect token: checks `aspects.admin.give` on the **holder** too (not just whoever ran the admin
  command) — a player who somehow obtains the token (e.g. an admin drops it, or another plugin transfers it)
  without that permission cannot use it to self-assign an Aspect; this is the same "never trust an item as
  proof of authorization" principle the spec states explicitly for GUIs and applies equally here. Opens a
  confirmation GUI (a generalized version of `RerollConfirmationGUI` — see "New shared infrastructure"
  below) showing "Become `<Aspect>`?" instead of "Reroll?".
- A generic selector: checks `aspects.admin.selector` on the holder, then opens `AspectSelectorGUI`.

### New shared infrastructure: a second, self-contained confirmation GUI (revised)

**Revised decision, made without a separate approval round:** rather than generalize `RerollConfirmationGUI`
(Phase 3 Batch 3a, already shipped and live-verified) into a shared abstract base class, Batch 4b adds a new,
self-contained `gui/TokenConfirmationGUI` that mirrors `RerollConfirmationGUI`'s structure (9-slot layout,
filler/confirm/cancel items, the duplicate-click guard, the "re-validate the held item at confirm-time,
consume only on success" atomicity pattern) without sharing code with it. This matches this project's actual
established convention more closely than the original plan above: every GUI class built so far
(`RerollConfirmationGUI`, `MainAspectGUI`, `AspectBrowserGUI`, `AspectDetailsGUI`) already duplicates its own
small `namedItem`/filler-building helpers rather than drawing from a shared `gui` utility class, despite four
GUI classes existing before this one — no such utility was ever introduced, because each GUI's needs differ
just enough (different slot counts, different item semantics) that a shared abstraction would need several
parameters and branches to serve all of them, trading a little duplication for a lot of indirection. Refactoring
`RerollConfirmationGUI` — already-shipped, already-live-verified code — to fit a new shared base carries real
risk for a marginal DRY benefit on a small amount of code; duplicating the pattern once more is the safer,
more-consistent-with-precedent choice. `AspectManager.assignViaToken` is still added as described below.

`AspectManager.assignViaToken(Player, String aspectId)` is a new, fourth thin wrapper (alongside
`adminSet`, `adminReroll`/`rerollViaItem`, and Batch 4a's `resetAspect`) around the existing `adminSet`
logic — it's functionally identical to `adminSet` (assign one specific, named Aspect, no exclusion logic
applies since the player is choosing, not rerolling randomly) and is added as a separate public method name
only so call sites read clearly ("assigning via a consumed token" vs. "an admin directly commanding a
reassignment"), even though both could share one more level of extraction if a future batch finds a reason
to.

### `AspectSelectorGUI`

Visually built like `AspectBrowserGUI` (Phase 3 Batch 3b) — one entry per enabled Aspect, same icon/rarity/
description/passive/active/cooldown lore — but clicking an entry calls `assignViaToken` and consumes the
held generic selector item (same atomicity/re-validation pattern as the confirmation GUIs), rather than
opening a read-only details view.

## Batch 4c — Administrative GUI

### `/aspects admin` (no further args) opens the GUI

Per the spec: "Open the administration GUI." Requires `aspects.admin` (already exists).

### Layout

A main admin menu (`gui/AdminGUI`) listing online players (one item per online player, using
`Player#getPlayerProfile()`/a player-head `ItemStack` showing their skin — standard Bukkit pattern for
"pick an online player" menus, not previously used in this plugin but a well-established, simple API:
`ItemStack` of `PLAYER_HEAD` with `SkullMeta#setOwningPlayer(player)`). Clicking a player opens a per-player
sub-menu (`gui/AdminPlayerMenuGUI`) showing: their current Aspect, and buttons for:
- **Assign a specific Aspect** → opens a picker (reuses the same Aspect-list-as-clickable-buttons pattern as
  `AspectSelectorGUI`/`AspectBrowserGUI`, but clicking calls `AspectManager.adminSet` directly, since this is
  an admin acting on someone else, not the player consuming their own item — no confirmation needed beyond
  what the spec requires below).
- **Force a reroll** → requires confirmation per the spec's "for destructive or potentially disruptive
  actions, require confirmation" (reuses the generalized confirmation GUI base from Batch 4b, parameterized
  around `adminReroll` instead of a consumed-item action — this path doesn't consume any item, so the base
  class's "held item" concept becomes optional/nullable for this call site).
- **Give a Reroller** → directly calls the same delivery logic `give-reroller` (Batch 4a) uses, no
  confirmation needed (giving an item is not destructive).
- **Clear cooldowns** → directly calls `AbilityCooldownManager.clearCache`, no confirmation needed.
- **Back** → returns to the player-list menu.

A **"View Aspect list"** button on the main admin menu (not nested under a specific player) reuses
`AspectBrowserGUI` directly — read-only, exactly as already built — satisfying "viewing a list of available
Aspects."

### Permission enforcement inside the GUI

Per the spec's explicit requirement — "only players with the correct administrative permissions may open or
interact with the admin GUI" and "never trust an item displayed in an inventory as proof of authorization" —
every destructive action inside `AdminGUI`/`AdminPlayerMenuGUI` re-checks the specific permission node for
that action (`aspects.admin.set`, `aspects.admin.reroll`, `aspects.admin.give`, `aspects.admin.cooldown`) on
the *viewing* player at click-time, not just once at GUI-open-time — a player's permissions could change
(e.g. a LuckPerms demotion) while the GUI is still open from an earlier, more-privileged state.

## What's deliberately out of scope here

WorldGuard integration remains Phase 5. No new config files are introduced in Phase 4 — the one new
`config.yml` key (`reset.immediate-reassign`) is added to the existing file, following the same
staged-validate pattern already in place.

## Testing approach

Argument parsing/validation that's genuinely pure (no Bukkit API calls) gets direct unit tests, exactly like
every prior phase's config/assignment-logic tests — e.g. the `give-reroller` amount-clamping logic (reject
≤0, clamp a request above 64 down to 64) is extracted as a small static method and unit-tested the same way
`VoidActiveAbility.pickSafeDistance` was. Everything else (command handlers that touch `CommandSender`/
`Player`, every GUI class) is manual-verification-only, consistent with this project's established split.
