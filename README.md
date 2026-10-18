# Aspects

A Paper plugin that gives every player on an SMP server a randomly assigned elemental
**Aspect** — a themed set of one passive effect and one activatable ability. Aspects
persist per player, can be rerolled or reassigned, and can be restricted by world and by
WorldGuard region.

## Requirements

- Paper (or a Paper fork) for Minecraft **1.21.x**
- Java 21+
- Optional: [WorldGuard](https://enginehub.org/worldguard) 7.0.x — purely a soft
  dependency, see [WorldGuard integration](#worldguard-integration) below

## Download

Every push to `main` is automatically built and tested, and the resulting plugin is
published as a ZIP at the repository's
[latest release](https://github.com/ErikKalstrom/Aspect/releases/latest) — always the
newest build, no manual steps.
The ZIP contains `Aspects.jar` (drop into `plugins/`), this README, and a
`default-configs/` folder with the shipped `config.yml`/`aspects.yml`/`messages.yml`/
`recipes.yml` for reference before installing.

(Note: this is currently a private repository — anyone the ZIP is shared with needs
read access to it, either as an invited collaborator or by the repo being made public.)

## Building

```bash
mvn clean package
```

The shaded jar is produced at `target/aspects-1.0.0-SNAPSHOT.jar`. Drop it into your
server's `plugins/` folder.

## The eight Aspects

| Aspect  | Rarity    | Theme                                           |
|---------|-----------|--------------------------------------------------|
| Wind    | Common    | Agility, jumping, and aerial movement            |
| Tide    | Common    | Water, regeneration, and endurance               |
| Fire    | Rare      | Aggressive damage and burning enemies            |
| Frost   | Rare      | Slowing and controlling opponents                |
| Earth   | Rare      | Defense, stability, and holding ground           |
| Thunder | Epic      | Speed, mobility, and explosive engagement        |
| Blood   | Epic      | Sacrifice, aggression, and overwhelming melee power |
| Void    | Legendary | Teleportation, deception, and positioning        |

New players are assigned a random enabled Aspect on first join, weighted by each
Aspect's own `weight` value in `aspects.yml` (rarity is a display label, not a separate
multiplier — the shipped weights are chosen so the aggregate chance per rarity tier
approximates Common 53% / Rare 27% / Epic 13% / Legendary 7%).
Every exact number (cooldowns, damage, percentages, weights) lives in `aspects.yml` and
can be retuned without touching code; any Aspect can also be disabled entirely.

**Activating an active ability:** sneak + swap-hands (the default "F" key), the only
reliable server-visible trigger Paper exposes for "use an item/ability while not
targeting a block."

## Commands

Player-facing (`/aspects`, alias `/aspect`):

| Command                      | Permission         | Description                          |
|-------------------------------|--------------------|---------------------------------------|
| `/aspects`                    | `aspects.gui`      | Open the Aspects menu                |
| `/aspects info`                | `aspects.use`      | Show your current Aspect             |
| `/aspects list`                | `aspects.use`      | List all enabled Aspects             |
| `/aspects inspect <player>`    | `aspects.inspect`  | View another player's Aspect         |
| `/aspects help`                | `aspects.use`      | Show the command list                |

Admin (`/aspects admin ...`, or `/aspects admin` alone to open the admin GUI):

| Subcommand                              | Permission               |
|-------------------------------------------|---------------------------|
| `admin` *(no args, opens the admin GUI)*  | `aspects.admin`           |
| `admin set <player> <aspect>`             | `aspects.admin.set`       |
| `admin reroll <player>`                   | `aspects.admin.reroll`    |
| `admin reset <player>`                    | `aspects.admin.reset`     |
| `admin give-reroller <player> [amount]`   | `aspects.admin.give`      |
| `admin give <player> <aspect>`            | `aspects.admin.give`      |
| `admin give-selection <player>`           | `aspects.admin.selector`  |
| `admin clear-cooldown <player>`           | `aspects.admin.cooldown`  |
| `admin reload`                            | `aspects.admin.reload`    |
| `admin list`                              | `aspects.admin`           |
| `admin debug <player>`                    | `aspects.admin.debug`     |

The admin GUI (`/aspects admin` with no arguments) lists online players and exposes
assign/reroll/give-reroller/clear-cooldown actions per player without needing to type
commands.

## Configuration

| File            | Purpose                                                                 |
|------------------|--------------------------------------------------------------------------|
| `config.yml`     | General settings: storage, first-join effects, world restrictions, reroll/reset behavior |
| `aspects.yml`    | Per-Aspect definitions: enabled state, rarity/weight, passive/active tuning |
| `messages.yml`   | All player-facing text and GUI titles/lore, editable and reloadable live |
| `recipes.yml`    | The Aspect Reroller item's crafting recipe                              |

`messages.yml`, `recipes.yml`, and the reveal-animation setting apply live via
`/aspects admin reload`. Changes to `aspects.yml`, the reroll/reset behavior flags,
`blocked-worlds`, or `respect-worldguard` require a server restart.

## WorldGuard integration

Aspects works identically with or without WorldGuard installed — it is a genuine soft
dependency (`softdepend` in `plugin.yml`, no hard `depend`).

When WorldGuard is present, Aspects registers one custom region flag,
`aspects-abilities` (default `ALLOW`):

```
/rg flag <region> aspects-abilities deny
```

Setting it to `deny` blocks **active-ability activation** in that region (e.g. Fire's
AOE burst, Void's teleport) and also blocks Void from teleporting a player *into* a
denied region. It does not affect passive effects (e.g. Earth's permanent Resistance)
or anything else WorldGuard already governs (building, PvP, etc.) — it is a dedicated
flag, not a reuse of WorldGuard's built-in `PVP` flag, so disabling PvP somewhere for
unrelated reasons doesn't silently also disable Aspect abilities there.

Set `world-restrictions.respect-worldguard: false` in `config.yml` to turn this check
off entirely (useful if WorldGuard is installed for unrelated reasons) without
uninstalling WorldGuard.

## Permissions

See `src/main/resources/plugin.yml` for the full, authoritative list — `aspects.use`
and `aspects.gui` default to all players; everything else (`aspects.inspect`,
`aspects.admin` and its children) defaults to server operators.

## Troubleshooting

- **"Failed to register the Reroller crafting recipe"** in the console — `recipes.yml`
  has an invalid pattern/ingredient combination (e.g. a pattern character with no
  matching ingredient entry, or an unknown material name). The Reroller won't be
  craftable until this is fixed; the recipe is otherwise left disabled rather than
  crashing the plugin.
- **"Reload failed — configuration errors"** from `/aspects admin reload` — the command
  prints each specific validation error and leaves the previously-loaded configuration
  active; it never partially applies a broken `aspects.yml`/`config.yml`. Fix the listed
  errors and reload again.
- **A staff member with a granted `aspects.admin.*` permission (via LuckPerms etc., not
  an operator) can't run a command** — every admin subcommand checks its own permission
  node independently of operator status, so this means the specific node for that
  subcommand (see [Permissions](#permissions)) isn't actually granted — check for typos
  or a conflicting deny elsewhere in the permission plugin's inheritance.
- **WorldGuard region flag `aspects-abilities` doesn't seem to do anything** — confirm
  `world-restrictions.respect-worldguard` is `true` in `config.yml` (requires a restart
  after changing) and that WorldGuard actually loaded before Aspects — check the startup
  log order; Aspects declares `softdepend: [WorldGuard]`, so WorldGuard should always
  load first if it's present at all.
- **The plugin won't even load with a "worldguard-bukkit"-related `NoClassDefFoundError`
  or `UnsupportedClassVersionError`** — this means a non-standard WorldGuard build is
  installed; WorldGuard is a soft dependency and Aspects works fine with it absent
  entirely, so when in doubt, remove the WorldGuard jar and confirm Aspects still loads
  cleanly before troubleshooting WorldGuard itself.
- **Nothing happens when sneaking and pressing F (swap hands)** — this only activates an
  ability when the main hand is empty; it's also debounced to one activation per client
  packet, so a second rapid press within the same tick is intentionally ignored, not a
  bug.

## Project docs

- `docs/stakeholder-interview-questions.md` — open design questions for whoever
  commissioned the plugin, covering balance numbers and judgment calls made during
  implementation.
- `docs/adding-a-new-aspect.md` — step-by-step guide for adding a 9th (or later) Aspect
  without touching the command/GUI/persistence layers.
- `docs/superpowers/specs/` and `docs/superpowers/plans/` — the design specs and
  implementation plans this plugin was built from, phase by phase.
