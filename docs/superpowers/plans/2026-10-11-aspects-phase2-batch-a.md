# Aspects Plugin — Phase 2 Batch A (Frost, Wind) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Frost and Wind Aspects to the already-shipped Phase 1 framework, plus the one small new piece Frost's passive needs (a damage-cause-cancelling listener), with no other changes to the existing architecture.

**Architecture:** See `docs/superpowers/specs/2026-10-11-aspects-phase2-batch-a-design.md` for the full rationale. In short: Frost and Wind reuse Phase 1's `Ability`/`PassiveAbility`/`AbilityManager`/`AbilityCooldownManager` framework exactly as-is (same pattern as Fire/Tide). The one addition is `PlayerDamageListener`, because Frost's "immune to powder-snow freezing" passive has no vanilla potion-effect equivalent the way Fire Resistance and Water Breathing did.

**Tech Stack:** Java 21, Maven, Paper API `1.21.11-R0.1-SNAPSHOT`, Adventure/MiniMessage, SQLite (unchanged, not touched by this plan).

## Global Constraints

- Minecraft/Paper target: **1.21.11**. Paper API coordinate: `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`, scope `provided`. Unchanged from Phase 1.
- Java: **21**. Package: `com.aspects.smp`. Main class: `com.aspects.smp.AspectsPlugin`.
- Ability activation trigger is **sneak + swap-hands** (`PlayerSwapHandItemsEvent`, the F key by default) with an empty main hand — this was fixed after Phase 1 shipped (the original right-click trigger never fires `PlayerInteractEvent` on open air with an empty hand, confirmed on a live Paper 1.21.11 server). `ActivationListener`, `AbilityManager`, and `AbilityCooldownManager` are unchanged by this plan; Frost's and Wind's active abilities plug into this exact same pipeline the same way Fire's and Tide's already do.
- Sound API: `Sound` is an interface in 1.21.11 — look sounds up via `Registry.SOUNDS.get(NamespacedKey.minecraft(name))` (already implemented in `SoundUtil`, reused unchanged here).
- Particle API: `Particle` remains a plain enum — `Particle.valueOf(name)` is valid (already implemented in `ParticleUtil`, reused unchanged here).
- Frost's "Frozen Heart" passive (immunity to powder-snow `FREEZE` damage specifically, not immunity to unrelated damage) has **no vanilla potion-effect equivalent**. It is implemented by a new `PlayerDamageListener` that cancels `EntityDamageEvent` when the cause is `FREEZE` and the damaged player's *current* Aspect has `passive.freeze-immunity` enabled — checked live on every event, not applied/removed on equip/unequip, since there is no lingering state.
- Wind's active ability computes its horizontal launch direction from the player's **yaw only** (`-sin(yaw), cos(yaw)`), never from `Location.getDirection()` (which is pitch-dependent and produces a zero-length horizontal vector — crashing `Vector.normalize()` — when the player looks straight up or down).
- Nothing in this plan touches Thunder, Void, Earth, or Blood — they remain disabled stubs in `aspects.yml`, unchanged, exactly as Phase 1 left them.
- Never claim in-game behavior is verified unless it was actually tested on a running server. Every task in this plan is build-verified only (no automated tests — same precedent as Fire/Tide in Phase 1, since these classes mutate live Bukkit entities and have no pure logic to unit test). Live verification against a real Paper server is the controller's responsibility after all tasks are complete and reviewed, not any individual task's implementer's — matching Phase 1 Task 15's precedent.

---

### Task 1: Enable Frost and Wind in aspects.yml

**Files:**
- Modify: `src/main/resources/aspects.yml`

**Interfaces:**
- Consumes: nothing new — `AspectsConfigParser.parseAspects` (already built, unit-tested in Phase 1) reads this file unchanged; no parser code changes needed.
- Produces: `aspects.yml` entries for `frost` and `wind` with `enabled: true` and real settings, consumed by `AspectsPlugin.buildRegistry` in Task 5 via `definitions.get("frost")` / `definitions.get("wind")`.

- [ ] **Step 1: Replace the `frost:` and `wind:` blocks in `src/main/resources/aspects.yml`**

Find the existing `frost:` block (currently `enabled: false` with no `passive`/`active` sections) and replace it with:

```yaml
  frost:
    enabled: true
    display-name: "<aqua>Frost</aqua>"
    description: "Slowing and controlling opponents."
    rarity: RARE
    weight: 20
    passive:
      freeze-immunity: true
    active:
      cooldown-seconds: 24
      radius: 5.0
      slowness-duration-ticks: 60
      slowness-amplifier: 1
      mining-fatigue-enabled: false
      mining-fatigue-ticks: 40
```

Find the existing `wind:` block (currently `enabled: false` with no `passive`/`active` sections) and replace it with:

```yaml
  wind:
    enabled: true
    display-name: "<white>Wind</white>"
    description: "Agility, jumping, and aerial movement."
    rarity: COMMON
    weight: 40
    passive:
      speed-boost: true
    active:
      cooldown-seconds: 16
      upward-velocity: 1.0
      forward-velocity: 0.6
```

Leave `thunder`, `void`, `earth`, and `blood` completely unchanged (still `enabled: false`, `weight: 0`, "Not yet implemented (planned for Phase 2)." description) — only `frost` and `wind` change in this file.

- [ ] **Step 2: Build to verify the resource is valid and no existing tests regress**

Run: `mvn -q test`
Expected: all existing tests still pass (the `AspectsConfigParserTest` tests use their own inline YAML via `StringReader`, not this resource file, so they are unaffected by this change — this run is just a regression safety net).

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/aspects.yml
git commit -m "$(cat <<'EOF'
Enable Frost and Wind in aspects.yml with real settings

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: PlayerDamageListener (Frost's freeze-immunity passive)

**Files:**
- Create: `src/main/java/com/aspects/smp/listener/PlayerDamageListener.java`

**Interfaces:**
- Consumes: `AspectsPlugin.getAspectManager()` (exists since Phase 1 Task 15); `AspectManager.getAspect(UUID) -> Optional<Aspect>` (exists since Phase 1 Task 12); `Aspect.definition() -> AspectDefinition` and `AspectDefinition.getBoolean(String, boolean)` (exist since Phase 1 Task 4).
- Produces: `PlayerDamageListener(AspectsPlugin)`, a `Listener` registered by `AspectsPlugin` in Task 5. This class is intentionally named generically (not `FreezeImmunityListener`) because a later Batch B plan will extend it with a second `@EventHandler` method for Blood's outgoing-melee-damage bonus — that extension is out of scope for this task.
- **Manual verification only** (no automated test): this listener only does anything meaningful against a real `EntityDamageEvent` with cause `FREEZE`, which requires a live server and powder snow.

- [ ] **Step 1: Create `src/main/java/com/aspects/smp/listener/PlayerDamageListener.java`**

```java
package com.aspects.smp.listener;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.Aspect;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public final class PlayerDamageListener implements Listener {
    private final AspectsPlugin plugin;

    public PlayerDamageListener(AspectsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FREEZE) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Aspect aspect = plugin.getAspectManager().getAspect(player.getUniqueId()).orElse(null);
        if (aspect == null) {
            return;
        }
        if (aspect.definition().getBoolean("passive.freeze-immunity", true)) {
            event.setCancelled(true);
        }
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `mvn -q package -DskipTests`
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/aspects/smp/listener/PlayerDamageListener.java
git commit -m "$(cat <<'EOF'
Add PlayerDamageListener to cancel FREEZE damage for Frost's passive

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Frost Aspect

**Files:**
- Create: `src/main/java/com/aspects/smp/ability/impl/FrostPassiveAbility.java`
- Create: `src/main/java/com/aspects/smp/ability/impl/FrostActiveAbility.java`

**Interfaces:**
- Consumes: `AbilityContext`, `PassiveAbility`, `Ability` (Phase 1 Task 8); `SoundUtil.play`, `ParticleUtil.spawn` (Phase 1 Task 10); `AspectDefinition.getDouble/getInt/getBoolean` (Phase 1 Task 4); the actual freeze-immunity *enforcement* comes from `PlayerDamageListener` (Task 2) — `FrostPassiveAbility` itself holds no state.
- Produces: `FrostPassiveAbility`, `FrostActiveAbility`.
- **Manual verification only** (no automated test), same reasoning as `FireActiveAbility`/`TideActiveAbility` in Phase 1.

- [ ] **Step 1: Create `src/main/java/com/aspects/smp/ability/impl/FrostPassiveAbility.java`**

```java
package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.PassiveAbility;
import com.aspects.smp.aspect.AspectDefinition;
import org.bukkit.entity.Player;

public final class FrostPassiveAbility implements PassiveAbility {
    @Override
    public void onEquip(Player player, AspectDefinition definition) {
        // Freeze immunity has no vanilla potion-effect equivalent. It is enforced
        // live by PlayerDamageListener checking the player's current Aspect on
        // each FREEZE damage event, so there is no state to apply here.
    }

    @Override
    public void onUnequip(Player player) {
        // No state to remove — see onEquip.
    }
}
```

- [ ] **Step 2: Create `src/main/java/com/aspects/smp/ability/impl/FrostActiveAbility.java`**

```java
package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.AbilityContext;
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.util.ParticleUtil;
import com.aspects.smp.util.SoundUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public final class FrostActiveAbility implements Ability {
    @Override
    public boolean activate(AbilityContext context) {
        Player caster = context.player();
        AspectDefinition definition = context.aspect().definition();

        double radius = definition.getDouble("active.radius", 5.0);
        int slownessDuration = definition.getInt("active.slowness-duration-ticks", 60);
        int slownessAmplifier = definition.getInt("active.slowness-amplifier", 1);
        boolean miningFatigueEnabled = definition.getBoolean("active.mining-fatigue-enabled", false);
        int miningFatigueTicks = definition.getInt("active.mining-fatigue-ticks", 40);

        List<Entity> nearby = new ArrayList<>(caster.getWorld().getNearbyEntities(
                caster.getLocation(), radius, radius, radius));
        for (Entity entity : nearby) {
            if (!(entity instanceof Player target) || target.getUniqueId().equals(caster.getUniqueId())) {
                continue;
            }
            if (target.getLocation().distance(caster.getLocation()) > radius) {
                continue;
            }
            target.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, slownessDuration, slownessAmplifier, true, true, true));
            if (miningFatigueEnabled) {
                target.addPotionEffect(new PotionEffect(
                        PotionEffectType.MINING_FATIGUE, miningFatigueTicks, 0, true, true, true));
            }
        }

        SoundUtil.play(caster, "block.glass.break", 1.0f, 1.2f, context.plugin().getLogger());
        ParticleUtil.spawn(caster.getLocation(), "SNOWFLAKE", 30, context.plugin().getLogger());
        return true;
    }
}
```

- [ ] **Step 3: Build to verify compilation**

Run: `mvn -q package -DskipTests`
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Run the full test suite to confirm no regressions**

Run: `mvn -q test`
Expected: all pre-existing tests still pass (26 from Phase 1).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/aspects/smp/ability/impl/FrostPassiveAbility.java src/main/java/com/aspects/smp/ability/impl/FrostActiveAbility.java
git commit -m "$(cat <<'EOF'
Implement the Frost Aspect (freeze immunity passive, Frost Nova active)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Wind Aspect

**Files:**
- Create: `src/main/java/com/aspects/smp/ability/impl/WindPassiveAbility.java`
- Create: `src/main/java/com/aspects/smp/ability/impl/WindActiveAbility.java`

**Interfaces:**
- Consumes: `AbilityContext`, `PassiveAbility`, `Ability` (Phase 1 Task 8); `SoundUtil.play`, `ParticleUtil.spawn` (Phase 1 Task 10); `AspectDefinition.getDouble/getBoolean` (Phase 1 Task 4).
- Produces: `WindPassiveAbility`, `WindActiveAbility`.
- **Manual verification only** (no automated test), same reasoning as Task 3.

- [ ] **Step 1: Create `src/main/java/com/aspects/smp/ability/impl/WindPassiveAbility.java`**

```java
package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.PassiveAbility;
import com.aspects.smp.aspect.AspectDefinition;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class WindPassiveAbility implements PassiveAbility {
    @Override
    public void onEquip(Player player, AspectDefinition definition) {
        if (!definition.getBoolean("passive.speed-boost", true)) {
            return;
        }
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, true, false, false));
    }

    @Override
    public void onUnequip(Player player) {
        player.removePotionEffect(PotionEffectType.SPEED);
    }
}
```

- [ ] **Step 2: Create `src/main/java/com/aspects/smp/ability/impl/WindActiveAbility.java`**

The horizontal launch direction is computed from the player's **yaw only**, never from `Location.getDirection()`. `getDirection()` is pitch-dependent and produces a zero-length horizontal vector when the player looks straight up or down, which crashes `Vector.normalize()`. Using `(-sin(yaw), cos(yaw))` avoids this entirely — it is already a unit vector for any yaw and needs no normalization.

```java
package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.AbilityContext;
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.util.ParticleUtil;
import com.aspects.smp.util.SoundUtil;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class WindActiveAbility implements Ability {
    @Override
    public boolean activate(AbilityContext context) {
        Player player = context.player();
        AspectDefinition definition = context.aspect().definition();

        double upwardVelocity = definition.getDouble("active.upward-velocity", 1.0);
        double forwardVelocity = definition.getDouble("active.forward-velocity", 0.6);

        double yawRadians = Math.toRadians(player.getLocation().getYaw());
        Vector horizontal = new Vector(-Math.sin(yawRadians), 0, Math.cos(yawRadians));
        Vector impulse = horizontal.multiply(forwardVelocity).setY(upwardVelocity);
        player.setVelocity(impulse);

        SoundUtil.play(player, "entity.bat.takeoff", 1.0f, 1.0f, context.plugin().getLogger());
        ParticleUtil.spawn(player.getLocation(), "CLOUD", 20, context.plugin().getLogger());
        return true;
    }
}
```

- [ ] **Step 3: Build to verify compilation**

Run: `mvn -q package -DskipTests`
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Run the full test suite to confirm no regressions**

Run: `mvn -q test`
Expected: all pre-existing tests still pass (26 from Phase 1).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/aspects/smp/ability/impl/WindPassiveAbility.java src/main/java/com/aspects/smp/ability/impl/WindActiveAbility.java
git commit -m "$(cat <<'EOF'
Implement the Wind Aspect (speed passive, Wind Leap active)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Wire Frost and Wind into AspectsPlugin, full build

**Files:**
- Modify: `src/main/java/com/aspects/smp/AspectsPlugin.java`

**Interfaces:**
- Consumes: everything from Tasks 1-4, plus the existing `AspectsPlugin` structure from Phase 1 (`buildRegistry`, `onEnable`, `onDisable` — read the current file before editing).
- Produces: nothing new for later tasks — this is the integration point for Batch A, matching Phase 1 Task 15's role.

- [ ] **Step 1: Read the current `src/main/java/com/aspects/smp/AspectsPlugin.java`**

(Already read during planning — shown below for reference. Your actual edit must be made against whatever the file currently contains; if it has drifted from this plan's expectation, stop and report BLOCKED rather than guessing.)

- [ ] **Step 2: Add two new imports**

Add these two import lines alongside the existing `com.aspects.smp.ability.impl.*` and `com.aspects.smp.listener.*` imports (keep them in the same alphabetically-grouped style as the existing imports):

```java
import com.aspects.smp.ability.impl.FrostActiveAbility;
import com.aspects.smp.ability.impl.FrostPassiveAbility;
import com.aspects.smp.ability.impl.WindActiveAbility;
import com.aspects.smp.ability.impl.WindPassiveAbility;
import com.aspects.smp.listener.PlayerDamageListener;
```

- [ ] **Step 3: Register `PlayerDamageListener` in `onEnable`**

Find this block:

```java
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new ActivationListener(abilityManager, this), this);
```

Add one more registration line after it:

```java
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new ActivationListener(abilityManager, this), this);
        getServer().getPluginManager().registerEvents(new PlayerDamageListener(this), this);
```

- [ ] **Step 4: Extend `buildRegistry` to register Frost and Wind**

Find this method body:

```java
    private AspectRegistry buildRegistry(Map<String, AspectDefinition> definitions) {
        AspectRegistry registry = new AspectRegistry();

        AspectDefinition fire = definitions.get("fire");
        if (fire != null) {
            registry.register(new Aspect(fire, new FirePassiveAbility(), new FireActiveAbility()));
        }
        AspectDefinition tide = definitions.get("tide");
        if (tide != null) {
            registry.register(new Aspect(tide, new TidePassiveAbility(), new TideActiveAbility()));
        }

        for (AspectDefinition definition : definitions.values()) {
            if (!definition.id().equals("fire") && !definition.id().equals("tide")) {
                getLogger().info("Aspect '" + definition.id() +
                        "' is defined in aspects.yml but not yet implemented in code; skipping registration.");
            }
        }
        return registry;
    }
```

Replace it with:

```java
    private AspectRegistry buildRegistry(Map<String, AspectDefinition> definitions) {
        AspectRegistry registry = new AspectRegistry();

        AspectDefinition fire = definitions.get("fire");
        if (fire != null) {
            registry.register(new Aspect(fire, new FirePassiveAbility(), new FireActiveAbility()));
        }
        AspectDefinition tide = definitions.get("tide");
        if (tide != null) {
            registry.register(new Aspect(tide, new TidePassiveAbility(), new TideActiveAbility()));
        }
        AspectDefinition frost = definitions.get("frost");
        if (frost != null) {
            registry.register(new Aspect(frost, new FrostPassiveAbility(), new FrostActiveAbility()));
        }
        AspectDefinition wind = definitions.get("wind");
        if (wind != null) {
            registry.register(new Aspect(wind, new WindPassiveAbility(), new WindActiveAbility()));
        }

        for (AspectDefinition definition : definitions.values()) {
            if (!definition.id().equals("fire") && !definition.id().equals("tide")
                    && !definition.id().equals("frost") && !definition.id().equals("wind")) {
                getLogger().info("Aspect '" + definition.id() +
                        "' is defined in aspects.yml but not yet implemented in code; skipping registration.");
            }
        }
        return registry;
    }
```

- [ ] **Step 5: Full build and test**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`, all 26 pre-existing unit tests pass, and `target/aspects-1.0.0-SNAPSHOT.jar` exists.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/aspects/smp/AspectsPlugin.java
git commit -m "$(cat <<'EOF'
Wire Frost and Wind into the registry and PlayerDamageListener into onEnable

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 7: Note for the controller, not this task's implementer**

Live verification against a running Paper 1.21.11 server (confirming: freeze immunity actually blocks powder-snow damage and nothing else; Frost Nova slows nearby enemies without affecting the caster; Wind Leap gives a capped jump with no flight, including when the player is looking straight up or down — the yaw-only direction fix from Task 4; `/aspects admin set <player> frost` and `wind` work via the existing commands with no changes needed there) is the controller's responsibility after this task's review passes, the same way Phase 1 Task 15's Step 4 was — do not attempt this as the task implementer; there is no running server in that environment.

---

## Post-implementation hardening (final whole-branch review)

After Task 5, a final whole-branch review (most-capable-model, base `35875d8`..`c464363`) found one Critical issue: `PlayerDamageListener` (Task 2) checked `aspect.definition().getBoolean("passive.freeze-immunity", true)` without first confirming the aspect was actually Frost. Since `AspectDefinition.getBoolean` returns the default (`true`) whenever the key is absent, and only Frost's `aspects.yml` entry has this key, **Fire, Tide, and Wind players were all getting free freeze immunity** — the bug came from this plan's own Task 2 code block, not from the implementer, and only surfaces once a second/third/fourth aspect without the key exists to expose the gap (which is exactly why the per-task review of Task 2 alone didn't catch it).

Fixed by extracting the decision into a package-visible static `PlayerDamageListener.cancelsFreezeDamage(Aspect)` that checks `aspect.id().equals("frost")` *before* the config lookup, so the `getBoolean(..., true)` default can only ever apply to Frost. Two new unit tests (`PlayerDamageListenerTest`) pin down the regression directly — `cancelsFreezeDamage` for a `fire`/`wind` aspect with an empty settings map must return `false`, which is exactly the input that returned `true` (incorrectly) before the fix.

While fixing this, the review's recommendation to also extract Wind's yaw-to-direction math (`WindActiveAbility.horizontalFromYaw(float)`) into a pure, unit-tested function was acted on too, since it directly addresses the same root cause (logic that could have been pure and tested was embedded in a Bukkit-API method instead, hiding a bug until integration). `WindActiveAbilityTest` covers 0/90/-90/180-degree yaw and a unit-length sweep. This raised the test count from 26 to 36.

One Important-severity recommendation was **not** acted on in this pass, deliberately deferred: `AspectsPlugin.buildRegistry`'s per-aspect `if` blocks plus a growing exclusion-list `&&` chain is still just cosmetic at 4 aspects, per the review, but was flagged as something that should become a `Map<String, Supplier<Aspect>>`-style factory table before Batch B adds four more aspects (Thunder, Void, Earth, Blood) — doing so now would be out of this batch's scope, but Batch B's own plan should address it first, before adding more copy-pasted registration blocks.

The remaining Minor findings (a hard-coded `"frost"` string literal coupling the passive to the aspect id rather than to a shared constant; no test for the event-handler wiring itself, only the pure decision function; `WindPassiveAbility.onUnequip` unconditionally stripping `SPEED` the same way Fire/Tide's passives already do; a cosmetic `ArrayList` copy in `FrostActiveAbility`) are left for the user to triage before or during Batch B; none block calling Batch A complete.

Live verification against a real Paper 1.21.11 server (Docker, same approach as Phase 1) confirmed:

- Clean startup with 4 enabled aspects (fire/tide/frost/wind) and no exceptions; `thunder`/`void`/`earth`/`blood` correctly logged as not-yet-implemented.
- **The exact regression this fix closed, reproduced and fixed on a live server**: a Tide player placed in a powder-snow column took freeze damage over time and eventually froze to death (`"FreezeTester2 froze to death"`), confirming non-Frost aspects are no longer immune. A Frost player placed in the same powder-snow column took zero damage over 40 seconds (vs. the Tide player's death by ~60 seconds of exposure), confirming the passive still works correctly for the aspect it belongs to.
- Wind Leap's velocity impulse was confirmed via the server's authoritative entity NBT data (`/data get entity ... Motion`), which showed a clear positive Y-velocity spike at the moment of activation, against the steady `-0.0784` gravity baseline beforehand. (A client-side bot using mineflayer did not visibly move during this test — mineflayer's own client-side physics prediction for its controlled entity does not reliably reflect server-pushed velocity corrections, which is a test-tooling limitation, not a plugin defect; the server-side NBT check sidesteps it and is the authoritative source of truth.)
- Frost Nova's multi-entity AOE behavior (slowing nearby enemies without affecting the caster) was not independently live-tested with a second entity in this pass, consistent with Phase 1's precedent for `FireActiveAbility`'s equivalent AOE logic (also never live-tested with a second entity, only code-reviewed) — recommended as a follow-up manual check alongside the rest of the Phase 1 checklist.
