# Adding a new Aspect

The Aspect system is registry-driven on purpose (see the `buildRegistry` refactor in
`docs/superpowers/specs/2026-10-11-aspects-phase2-batch-b1-design.md`) so a new Aspect is
a small, additive change: one config entry, two small classes, and a one-line
registration — never a change to `AbilityManager`, the commands, the GUIs, or the
persistence layer.

This walks through adding a 9th Aspect, `storm` (pick your own id/theme), end to end.

## 1. Define it in `aspects.yml`

```yaml
aspects:
  storm:
    enabled: true
    display-name: "<yellow>Storm</yellow>"
    description: "Your own description."
    rarity: EPIC
    weight: 10
    icon: FEATHER
    passive:
      name: "Static Charge"
      description: "A short player-facing description of what the passive does."
      your-flag: true
    active:
      name: "Thunderclap"
      description: "A short player-facing description of what the active ability does."
      cooldown-seconds: 20
      your-own-number: 3.0
```

- `rarity` must be one of the five `AspectRarity` values: `COMMON`, `UNCOMMON`, `RARE`,
  `EPIC`, `LEGENDARY`. `weight` is a relative number used when picking a random Aspect —
  it doesn't need to add up to anything across Aspects, but the shipped Aspects' weights
  are deliberately chosen so each rarity tier's *aggregate* share approximates the
  product spec's suggested distribution (see `aspects.yml`'s own header comment and
  `README.md`). If you want your new Aspect's rarity label to mean what it says, pick a
  weight consistent with that tier's existing Aspects, not an arbitrary number.
- `icon` must be a real, non-air `Material` name (`Material.matchMaterial(...)` must
  resolve it, and it's rejected if it resolves to an `*_AIR` material) — it's what
  represents this Aspect as an item in the GUIs.
- Everything under `passive:` and `active:` is free-form **except two reserved keys each**:
  `passive.name`/`passive.description` and `active.name`/`active.description`. These are
  read generically by `AspectDefinition` (via `passiveName()`/`passiveDescription()`/
  `activeName()`/`activeDescription()`) and shown everywhere a player sees an Aspect —
  every GUI, `/aspects info`, and the first-join reveal. Leaving them out falls back to a
  generic placeholder like "Storm Passive" with no description, which looks exactly like
  a broken/unfinished Aspect; always set real values for a real Aspect. Beyond those two
  keys per block, name the rest however makes sense for your ability. The one *active*
  key every Aspect gets for free, read generically by `AbilityManager` itself (not by
  your ability class), is `active.cooldown-seconds` — you never implement cooldown logic
  yourself.

## 2. Write the passive ability

Create `src/main/java/com/aspects/smp/ability/impl/StormPassiveAbility.java` implementing
`PassiveAbility`:

```java
public final class StormPassiveAbility implements PassiveAbility {
    @Override
    public void onEquip(Player player, AspectDefinition definition) {
        if (!definition.getBoolean("passive.your-flag", true)) {
            return;
        }
        // apply your sustained effect, e.g. player.addPotionEffect(...)
    }

    @Override
    public void onUnequip(Player player) {
        // remove exactly what onEquip applied
    }
}
```

`onEquip` runs on first join, on admin set/reroll, and on respawn (to reapply a
potion-effect-based passive that respawning clears); `onUnequip` runs when the player
loses this Aspect. See `WindPassiveAbility` for the simplest real example (a plain
`PotionEffectType.SPEED` toggle).

**If your passive needs to react to a game event instead of holding a sustained potion
effect** (like Blood's melee bonus, Void's fall-damage reduction, or Frost's freeze
immunity), it does **not** get its own listener class. Add a branch to the existing
listener that already owns that event type (`PlayerDamageListener` for anything
damage-related) — always checking `aspect.id().equals("storm")` **before** reading any
of your config flags. This check-id-first order is mandatory: an earlier version of
Frost's freeze immunity read its config flag before checking the Aspect id, which
silently granted the behavior to every Aspect, not just Frost. Every handler added
since follows this order; yours must too.

## 3. Write the active ability

Create `src/main/java/com/aspects/smp/ability/impl/StormActiveAbility.java` implementing
`Ability`:

```java
public final class StormActiveAbility implements Ability {
    @Override
    public boolean activate(AbilityContext context) {
        Player caster = context.player();
        AspectDefinition definition = context.aspect().definition();
        double value = definition.getDouble("active.your-own-number", 3.0);

        // do the thing, e.g. SoundUtil.play(...), ParticleUtil.spawn(...), deal damage, etc.

        return true; // false means "nothing happened, don't start the cooldown"
    }
}
```

`AbilityContext` gives you the `Player`, the resolved `Aspect` (so `context.aspect()
.definition()` reads your `aspects.yml` values), and the plugin instance (for the
scheduler, `getProtectionGate()`, `getLogger()`, etc.). Returning `false` is how an
ability says "could not find anything useful to do" (compare Void's `pickSafeDistance`
returning 0) — `AbilityManager` only starts the cooldown when `activate` returns `true`.

Use `SoundUtil.play(...)` and `ParticleUtil.spawn(...)` (see any existing
`*ActiveAbility`) for feedback — every active ability in this plugin plays at least one
sound and one particle effect, matching the original brief's requirement that every
active ability have audible/visual feedback.

If your ability teleports, damages, or otherwise needs to respect world protection,
route it through `context.plugin().getProtectionGate()` — never call
`WorldGuardIntegration` directly (see `VoidActiveAbility` for the pattern), so the
`respect-worldguard` config toggle keeps working for your ability too.

**If your ability affects another player** (damage, a debuff, anything offensive), you
need a second, separate check: `ProtectionGate.isLocationAllowed(...)` only gates whether
*this plugin's own* `aspects-abilities` flag permits activating the ability at all — it
says nothing about whether the *target* may actually be hit (world PvP, WorldGuard's
real `PVP` flag, WorldGuard region membership). For every target you're about to affect,
also call `context.plugin().getProtectionGate().canTargetPlayer(caster, target)` and skip
that target if it returns `false` — see `FireActiveAbility`/`FrostActiveAbility` for the
pattern (loop over nearby players, check radius, then `canTargetPlayer`, then act).
- If your effect goes through `LivingEntity#damage(...)` (like Fire), that call already
  fires a real damage event other protection plugins can observe and cancel — no extra
  work needed.
- If your effect does **not** go through a damage call (a potion effect, a knockback, a
  teleport of the target, etc. — like Frost's slowness), other protection plugins never
  get a chance to see it. Fire a synthetic zero-damage `EntityDamageByEntityEvent` first
  (cause `MAGIC`, not `ENTITY_ATTACK` — a melee-specific cause can trip anti-cheat/combat
  plugins scrutinizing it for reach/direction) and skip the target if
  `event.isCancelled()`, exactly like `FrostActiveAbility` does. Never skip this step just
  because `canTargetPlayer` already returned `true` — that only covers WorldGuard, not
  every other protection plugin a server might run.

## 4. Register it

In `AspectsPlugin.java`, add one entry to each factory map, keyed by the same id used
in `aspects.yml`:

```java
private static final Map<String, Supplier<PassiveAbility>> PASSIVE_FACTORIES = Map.of(
        // ...existing entries...
        "storm", StormPassiveAbility::new
);
private static final Map<String, Supplier<Ability>> ACTIVE_FACTORIES = Map.of(
        // ...existing entries...
        "storm", StormActiveAbility::new
);
```

This is the only place a config id is wired to real Java classes. An `aspects.yml`
entry with no matching factory is logged at startup ("defined in aspects.yml but not
yet implemented in code; skipping registration") and simply excluded, rather than
crashing — so it's safe to stage a new Aspect's config before its code exists, or to
disable one by removing its factories without touching `aspects.yml`.

If Aspect Mastery is enabled (`mastery.yml`), your new Aspect's activations are tracked
automatically the moment it's registered — `AbilityManager` records a successful
activation generically by Aspect id after `activate()` returns `true`, so there is
nothing to wire up yourself.

## 5. Test it

- Extract any pure logic (math, distance/clamp calculations, selection rules) into a
  small static method and unit test it with fakes, the way `VoidActiveAbility
  .pickSafeDistance` and `ThunderActiveAbility.effectiveDistance` do — these are the
  only parts of an ability that don't need a live Bukkit `Player`/`World`.
- Everything that does need a live Bukkit environment (potion effects, damage, particles,
  teleports, GUIs) is manual-verification-only in this project, consistent with every
  other ability — build the jar, deploy to a test server, and verify by hand (`/aspects
  admin set <player> storm`, trigger the ability, check `/aspects admin debug <player>`
  for cooldown state).

## Checklist

- [ ] New entry under `aspects:` in `aspects.yml` (enabled, display-name, description,
      rarity, weight chosen to match its rarity tier's existing Aspects, icon,
      `passive.name`/`passive.description`, `active.name`/`active.description`, and your
      own `passive.*`/`active.*` keys)
- [ ] New `*PassiveAbility` class in `ability/impl/` (or a branch in an existing
      listener, with the Aspect-id check first)
- [ ] New `*ActiveAbility` class in `ability/impl/`, routed through `ProtectionGate` if
      it affects the world, and through `canTargetPlayer` for every target if it
      affects another player
- [ ] Both registered by id in `AspectsPlugin`'s `PASSIVE_FACTORIES`/`ACTIVE_FACTORIES`
- [ ] Unit tests for any pure logic the active ability extracts
- [ ] `mvn clean package` succeeds and the full test suite still passes
- [ ] Manually verified live: passive applies/removes correctly on join/reroll/respawn,
      active ability works and respects its cooldown and `ProtectionGate`
