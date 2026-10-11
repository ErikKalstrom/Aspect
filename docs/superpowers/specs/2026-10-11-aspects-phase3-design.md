# Aspects Plugin — Phase 3 (Reroller Item, Crafting, Player GUIs) Design

Date: 2026-10-11

## Context

Phase 2 is complete: all 8 Aspects are implemented, tested, reviewed, and live-verified. Phase 3 is the next
unstarted piece of the original functional spec — the player-facing self-service reroll path (craft an item,
right-click it, confirm in a GUI) and the player-facing informational GUIs (main menu, Aspect browser). The
admin GUI and the full admin/permission command suite remain Phase 4, and WorldGuard integration remains
Phase 5, per the phasing already laid out in `2026-10-11-aspects-plugin-design.md`.

The exact requirements below are pulled directly from the original project prompt (sections 5–8), which is the
project's source of truth for *what* to build; this document is only about *how* and *in what order*.

**Scoping decision, made without a separate approval round per this session's standing instruction to keep
working autonomously:** this phase is split into two batches, same as Phase 2's batches:

- **Batch 3a — Reroller item, crafting recipe, reroll confirmation flow.** This is the complete, atomic
  "craft it, use it, get a new Aspect" mechanic: a new `item/` package, a new `recipe/` package, a new
  `gui/` package containing only `RerollConfirmationGUI` and the shared `GUIListener`, a new `recipes.yml`
  config file, and the player-initiated reroll path on `AspectManager`. This batch is independently
  shippable and testable — a player can craft a Reroller and use it — without any of the informational GUIs
  existing yet.
- **Batch 3b — Main Aspects GUI, Aspect Browser GUI, Aspect Details GUI.** Purely informational/navigational
  UI built on top of the `GUIListener` infrastructure from 3a. `/aspects` (currently a text command) becomes
  a GUI-opener; the existing text-info behavior moves to a new `/aspects info` subcommand to match the
  original spec's command list exactly.

Splitting this way mirrors the reasoning from Batch B2: the reroll mechanic alone is comparable in size and
risk (new item-identification system, new recipe system, new exploit-prevention requirements) to a full
Aspect batch, so it gets its own implementation plan and review cycle rather than being bundled with the
purely-visual GUI work.

## Batch 3a — Reroller item, crafting, confirmation

### Item identification (`item/CustomItemManager`, `item/RerollerItem`)

- **Material:** `AMETHYST_SHARD` (exact per spec).
- **Display name:** `"<gradient:dark_purple:aqua>✦ Aspect Reroller</gradient>"` (MiniMessage, matching the
  project's established dark-purple/aqua palette and the spec's example name).
- **Lore** (exact three lines from the spec, each its own `Component`, non-italic — Bukkit renders lore
  italic by default, which must be explicitly disabled per line so it matches the clean style the rest of
  the plugin uses):
  - `"A fragment of unstable elemental energy."`
  - `"Right-click to abandon your current Aspect."`
  - `"A new random Aspect will replace it."`
- **Enchant glint without a functional enchantment:** add `ItemFlag.HIDE_ENCHANTS` plus
  `ItemMeta.setEnchantmentGlintOverride(true)` (the modern, supported Paper API for this exact "glint but no
  enchant" requirement — no more hacky "enchant with a hidden level-0 curse" workarounds needed on
  1.21.11).
- **Secure identification via `PersistentDataContainer`:** a single `NamespacedKey` (`aspects:reroller`,
  value `true`, `PersistentDataType.BOOLEAN`) stamped on the item's `ItemMeta`. `CustomItemManager.isReroller(ItemStack)`
  is the *only* code path anything is allowed to use to decide whether an item is a valid Reroller — never a
  name or lore string comparison, matching the spec's explicit "never identify a valid reroller based solely
  on its name or lore" and "prevent renamed or forged ordinary amethyst shards from functioning as
  rerollers."
- `CustomItemManager.createReroller() -> ItemStack` builds a fresh, correctly-tagged stack (amount 1) — the
  single construction point, so the item's appearance and tag are defined in exactly one place.

### Crafting recipe (`recipe/RerollerRecipeManager`, `recipes.yml`)

Default shaped recipe, exactly as specified:

```
Amethyst Shard | Diamond       | Amethyst Shard
Diamond        | Nether Star   | Diamond
Amethyst Shard | Diamond       | Amethyst Shard
```

Result: 1 Aspect Reroller (the custom-tagged stack from `CustomItemManager.createReroller()`, never a plain
amethyst shard).

- `recipes.yml` structure:
  ```yaml
  reroller:
    enabled: true
    pattern:
      - "ADA"
      - "DND"
      - "ADA"
    ingredients:
      A: AMETHYST_SHARD
      D: DIAMOND
      N: NETHER_STAR
    result-amount: 1
  ```
  This is directly configurable per the spec's "allow server owners to change the ingredients, pattern, and
  result amount" and "support a configurable alternative recipe if the server does not want to use Nether
  Stars" — a server owner swaps the `N:` ingredient mapping, no code change needed. Setting `enabled: false`
  disables crafting entirely (recipe never registered).
- Registered in `onEnable` via `Bukkit.addRecipe(...)` using a `NamespacedKey` owned by the plugin
  (`aspects:reroller_recipe`), and explicitly unregistered via `Bukkit.removeRecipe(...)` before
  re-registering on `/aspects admin reload` (Phase 4's reload command will call this; Phase 3 only needs the
  registration to be idempotent-safe, i.e. always remove-then-add, so a future reload doesn't duplicate it).
- Validation at config-load time (extends `ConfigManager`'s existing staged-validate-then-swap pattern):
  pattern must be exactly 3 rows of exactly 3 characters; every character used in the pattern (other than
  space, which means "empty slot") must have a corresponding entry in `ingredients`; every ingredient value
  must be a valid `Material` name. Collect all errors, same as `AspectsConfigParser` does today.
- Shift-click safety: Bukkit's crafting-table recipe system already handles shift-click-to-craft-many
  correctly for shaped recipes with no plugin code needed (it crafts using the registered `ItemStack`
  result as-is, repeating while ingredients last) — the spec's "ensure shift-click crafting cannot generate
  invalid or duplicated results" is satisfied by *not* implementing any custom crafting-table UI or
  `PrepareItemCraftEvent` interference; the result `ItemStack` is always the single correctly-tagged stack
  from `createReroller()`, so there is no way to "shift-click exploit" a different result.

### Reroll confirmation flow (`gui/RerollConfirmationGUI`, `gui/GUIListener`)

- **Trigger:** `PlayerInteractEvent` (right-click, main hand, matching every other "holding an item and
  right-clicking" interaction already in this plugin) where `CustomItemManager.isReroller(item)` is true.
  Cancel the event (no placing/using the amethyst shard as a block interaction) and open
  `RerollConfirmationGUI` for that player.
- **GUI contents:** a small (size-9, one row) inventory. Slot 4 (center): an item showing the player's
  *current* Aspect (its icon material — reuse the per-Aspect icon concept the Aspect Browser also needs, see
  Batch 3b's `AspectDefinition` icon field) with lore explaining "this will be replaced." Slot 2: a green
  confirm button (`LIME_WOOL` or `LIME_CONCRETE`, name `"<green>Confirm Reroll</green>"`). Slot 6: a red
  cancel button (`RED_WOOL`, name `"<red>Cancel</red>"`). Remaining slots: a neutral filler pane
  (`GRAY_STAINED_GLASS_PANE`, empty name) so the whole row looks intentional, matching the spec's "polished"
  requirement.
- **`GUIListener`** is the single shared `@EventHandler` for `InventoryClickEvent` and `InventoryCloseEvent`
  across *every* GUI this plugin will ever have (3a's confirmation GUI now; 3b's three GUIs later; Phase 4's
  admin GUI later still) — not one listener per GUI type. It identifies which GUI is open via a small
  `Map<InventoryHolder-marker-interface, ...>` dispatch: each GUI class implements a shared
  `AspectsGuiHolder` interface (just a marker, `InventoryHolder` with an added `onClick(InventoryClickEvent)`
  / `onClose(InventoryCloseEvent)` pair of methods) and is itself an `InventoryHolder` set on the `Inventory`
  it creates. `GUIListener.onClick` does `if (event.getInventory().getHolder() instanceof AspectsGuiHolder holder) { event.setCancelled(true); holder.onClick(event); }` — cancelling *every* click unconditionally before
  delegating is what satisfies the spec's "protect GUI inventories against item stealing and shift-click
  exploits" for every current and future GUI, in one place, by construction (a GUI class cannot forget to
  cancel the event, because it never gets the chance not to).
- **Confirm button:** calls a new `AspectManager.rerollViaItem(Player player) -> Optional<Aspect>` (see
  below), and only on a *non-empty* result does it: consume exactly one Reroller from the player's hand
  (`ItemStack.subtract(1)` on the exact slot that was being held when the GUI opened, re-validated with
  `isReroller` at confirm-time, not just at open-time — the spec's "prevent normal reroller items from being
  consumed by unrelated interactions" and the atomicity requirement "a failed reroll must not consume the
  item without assigning a new Aspect"), close the inventory, and show the reveal message/sound/particle
  (reusing the same message keys and particle/sound pattern as first-join and admin-set reveals). If
  `rerollViaItem` returns empty (no eligible Aspect — e.g. every other Aspect disabled), show an error
  message and do **not** consume the item, matching "a failed reroll must not consume the item."
- **Cancel button or closing the GUI (`InventoryCloseEvent`) with no confirm click:** no state change, no
  item consumed — the spec's explicit "if the player closes the GUI, treat that as cancellation."
- **Duplicate-click / rapid-click guard:** a `Set<UUID>` of "reroll confirmation currently being processed"
  on `RerollConfirmationGUI` (or a tiny dedicated tracker), checked and set atomically at the start of the
  confirm handler and cleared when the operation finishes (success or failure) — a second click on Confirm
  while the first is still being processed is ignored outright. Since `rerollViaItem` and the inventory
  consumption both run synchronously on the main thread inside the event handler (no async gap between
  "check if reroller" and "consume it"), there is no real race window for a single player's clicks, but the
  guard is cheap insurance against double-fire from client-side double-packets, matching the spec's "prevent
  duplicate rerolls from rapid clicking."
- **Disconnecting mid-confirmation:** `PlayerQuitEvent` already closes any open inventory as a side effect of
  the player disconnecting (Bukkit's own behavior) which fires `InventoryCloseEvent` — treated as
  cancellation by the existing close-handler logic above, satisfying "disconnecting during a reroll
  confirmation does not consume a reroller" with no extra code.

### `AspectManager.rerollViaItem`

A new method, deliberately *not* a copy-paste of `adminReroll` — it's the same underlying operation
(`assignmentService.selectRandom` with `excludeCurrentOnReroll` honored, unequip old passive, persist, clear
cooldown + temporary-effect caches, equip new passive) so both `adminReroll` and `rerollViaItem` are
refactored to share one private `performReroll(Player, excludeCurrent: boolean)` helper. The two public
methods become thin wrappers: `adminReroll` always excludes the current Aspect when `excludeCurrentOnReroll`
is configured (existing behavior, unchanged); `rerollViaItem` does the same (the spec doesn't distinguish
item-triggered rerolls from admin-triggered ones for the exclude-current rule). This refactor touches
existing, already-tested code — the per-task review for this task must re-run `AspectManager`'s existing
behavior assumptions (there's no dedicated `AspectManagerTest` today, so this is manual-verification-only,
consistent with the class's current state) and confirm `adminReroll`'s observable behavior is bit-for-bit
unchanged before and after the extraction.

### Right-click trigger vs. the ability-activation trigger

This plugin already uses **sneak + swap-hands** for activating an Aspect's active ability (a Phase 1
hardening fix, because plain right-click-into-air never fires a usable Paper event). The Reroller's
right-click trigger is different: it's a right-click *while holding a specific item*, which **does** fire a
normal, reliable `PlayerInteractEvent` with a valid `Action` and non-null `item` on Paper — this is not the
same "right-click into empty air" case that forced the sneak+swap workaround, so no special trigger gymnastics
are needed here. (This is also exactly how real plugins implement "right-click this custom item to do X,"
including vanilla's own enchanted-book-equivalent interactions.)

## Batch 3b — Main Aspects GUI, Aspect Browser, Aspect Details

### `AspectDefinition` gains an `icon` field

The spec requires each Aspect's GUI entry to have a representative icon. `AspectDefinition` (a record) gains
one more component: `Material icon`, read from a new `aspects.yml` key `icon:` per Aspect (e.g. `fire.icon:
BLAZE_POWDER`, `void.icon: ENDER_PEARL`, `blood.icon: REDSTONE`), validated at config-load time the same way
`rarity:` already is (must parse to a real `Material`). This is the only schema change to `aspects.yml` in
Phase 3. Sensible defaults are chosen per-Aspect and documented in the implementation plan's exact task
brief (not guessed ad hoc by whichever subagent implements it).

### `/aspects` command changes

- `/aspects` (no args) now opens `MainAspectGUI` for the invoking player (was: printed text info). Console
  invocation with no args prints the existing "only players..." message, now pointed at the GUI instead of
  at `info`.
- `/aspects info` (new subcommand) gets the *exact* text-output behavior `AspectsCommand.handleInfo` has
  today — this is a pure extraction, not new logic, so it's a mechanical, low-risk task.
- `/aspects admin` is unaffected (unchanged in Batch 3b; the Admin GUI is Phase 4).

### `MainAspectGUI`

Size-27 (3 rows) inventory. Contents, per the spec's bullet list:
- Center-top area: an item representing the player's current Aspect (their `icon` material), lore showing
  rarity (colored per `AspectRarity`'s existing color mapping — already used in messages, reused here),
  passive ability name/description, active ability name/description, and current cooldown remaining (0 if
  ready) read from the existing `AbilityCooldownManager.remainingMillis`.
- A "Browse Aspects" button (icon: `BOOK`) that closes this GUI and opens `AspectBrowserGUI`.
- A "How to craft a Reroller" button (icon: `AMETHYST_SHARD`, *not* a real functional Reroller — a plain
  undecorated stack so it can never be confused with or mistaken for a real one) whose lore spells out the
  crafting pattern from `recipes.yml` in human-readable form (e.g. "Amethyst Shard, Diamond, Amethyst Shard /
  Diamond, Nether Star, Diamond / ...", generated from the live config so it stays correct if a server owner
  changes the recipe, rather than being a hardcoded string).
- Filler panes for visual polish, same `GRAY_STAINED_GLASS_PANE` convention as the confirmation GUI.
- All clicks are no-ops except the two buttons above (everything else in the GUI is inert/decorative) — the
  shared `GUIListener`'s unconditional cancel already prevents item extraction; `MainAspectGUI.onClick` only
  needs to branch on which slot was clicked.

### `AspectBrowserGUI`

A paginated-if-needed (9 Aspects or fewer fits one 54-slot double chest with room for nav buttons, so no real
pagination logic is needed for 8 Aspects — but the layout reserves the bottom row for a future "next page"
button so adding a 9th+ Aspect later doesn't require a GUI redesign) inventory showing one item per *enabled*
Aspect: icon, name, rarity, short description, passive, active, default cooldown — all read-only, pulled
directly from `AspectDefinition`. Clicking an Aspect's item opens `AspectDetailsGUI` for that specific Aspect
(a closer look, not a selection — the spec is explicit that "clicking another Aspect should show its details
but must not change the player's Aspect"). A "Back" button returns to `MainAspectGUI`.

### `AspectDetailsGUI`

A single-Aspect detail view (size-9 or size-27, final size decided in the implementation plan once the exact
information density is drafted) — center item mirrors the Browser's entry for that Aspect but with more
room for description text (not truncated), a "Back" button returning to `AspectBrowserGUI`. Clicking has
no effect except the Back button — this GUI makes no state changes at all, by design.

## Shared infrastructure summary (new in Phase 3)

- **`item/CustomItemManager`** — single source of truth for creating and identifying the Reroller item
  (and, per the original package sketch, future custom items like the Aspect Selector token, which is
  explicitly Phase 4 scope — `/aspects admin give-selection` — not built here).
- **`item/RerollerItem`** — a thin wrapper/constant-holder around the Reroller's specific appearance data
  (display name, lore, material), consumed by `CustomItemManager`.
- **`recipe/RerollerRecipeManager`** — registers/unregisters the shaped recipe from `recipes.yml`.
- **`config/RecipesConfig`** (record) + parser additions — follows the exact staged-validate-swap pattern
  `ConfigManager` already uses for `config.yml`/`aspects.yml`.
- **`gui/AspectsGuiHolder`** (new interface) — marker + click/close callback contract every GUI implements.
- **`gui/GUIListener`** — the one `@EventHandler` pair (`InventoryClickEvent`, `InventoryCloseEvent`) for
  every GUI, present and future.
- **`gui/RerollConfirmationGUI`** (3a), **`gui/MainAspectGUI`**, **`gui/AspectBrowserGUI`**,
  **`gui/AspectDetailsGUI`** (3b).
- **`AspectManager.performReroll`** (private, shared by `adminReroll` and the new `rerollViaItem`).

## What's deliberately out of scope here

The Admin GUI, the full admin/permission command suite (`give-reroller`, `admin reset`,
`admin clear-cooldown`, `admin reload`, `admin list`, `admin give-selection`, `admin debug`, and the full
permission node list from spec section 7), and the Aspect Selector item/token all remain Phase 4. WorldGuard
integration remains Phase 5. `/aspects list` (a plain-text alternative to the Browser GUI) is not implemented
in Phase 3 — it is listed under the same spec section as the other player commands, naturally bundled with
the rest of the player-command polish work in Phase 4, since it's a text-formatting task with no GUI
dependency and no urgency relative to the GUI work.

## Testing approach

- `CustomItemManager.isReroller`/`createReroller` round-trip (create then check `isReroller` returns true;
  check a plain `AMETHYST_SHARD` stack with no PDC tag returns false; check a stack with the right name/lore
  but no PDC tag returns false) is pure, Bukkit-`ItemStack`-dependent but mockable-free if `ItemStack`/
  `ItemMeta`/`PersistentDataContainer` round-trip correctly in a unit test without a live server — this needs
  checking against this project's established "no Bukkit mocking" convention; if `ItemStack`/`ItemMeta`
  construction doesn't work standalone outside a running server (likely, since `Bukkit.getItemFactory()`
  requires a running server instance), this becomes manual-verification-only like every other Bukkit-API-
  dependent code in this project, and the plan will say so explicitly rather than attempting a mock.
- The recipe config parser (pattern/ingredient validation) is pure and unit-tested exactly like
  `AspectsConfigParser`'s existing tests — no Bukkit dependency in the validation logic itself (only
  `Material.matchMaterial(name) != null` is needed to validate an ingredient name, which — unlike
  `ItemStack`/`ItemMeta` — **is** safe to call without a running server, since `Material` is a plain enum).
- `AspectManager.performReroll`'s extraction is manual-verification-only (no existing test suite for this
  class to extend), verified by confirming `adminReroll`'s existing live-tested behavior is unchanged.
- The GUI classes themselves (layout, click handling) are manual-verification-only, consistent with every
  other Bukkit-API-mutation code in this project.
