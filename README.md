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
| Frost   | Common    | Slowing and controlling opponents                |
| Fire    | Rare      | Aggressive damage and burning enemies            |
| Tide    | Rare      | Water, regeneration, and endurance               |
| Earth   | Rare      | Defense, stability, and holding ground           |
| Thunder | Epic      | Speed, mobility, and explosive engagement        |
| Blood   | Epic      | Sacrifice, aggression, and overwhelming melee power |
| Void    | Legendary | Teleportation, deception, and positioning        |

New players are assigned a random enabled Aspect (weighted by rarity) on first join.
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

## Project docs

- `docs/stakeholder-interview-questions.md` — open design questions for whoever
  commissioned the plugin, covering balance numbers and judgment calls made during
  implementation.
- `docs/superpowers/specs/` and `docs/superpowers/plans/` — the design specs and
  implementation plans this plugin was built from, phase by phase.
