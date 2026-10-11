# Aspects Plugin — Phase 2 Batch B1 (Earth, Thunder) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Earth and Thunder Aspects to the Phase 1 + Batch A framework, and refactor `AspectsPlugin.buildRegistry` into a factory-table pattern before adding them, per Batch A's final-review recommendation.

**Architecture:** See `docs/superpowers/specs/2026-10-11-aspects-phase2-batch-b1-design.md` for full rationale. In short: Earth reuses the existing self-buff pattern exactly (no new mechanic — its 30s cooldown already exceeds its 3s buff duration, so no new "single active" infrastructure is needed). Thunder needs one new piece of logic — a ray-trace-and-clamp wall-safety check for its dash — built as a pure, unit-tested function plus a thin Bukkit-API wrapper. A new shared `DirectionUtil` is extracted from Batch A's `WindActiveAbility` since Thunder needs the identical safe yaw-to-vector math.

**Tech Stack:** Java 21, Maven, Paper API `1.21.11-R0.1-SNAPSHOT`, Adventure/MiniMessage, SQLite (unchanged, not touched by this plan).

## Global Constraints

- Minecraft/Paper target: **1.21.11**. Paper API coordinate: `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`, scope `provided`. Unchanged from Phase 1/Batch A.
- Java: **21**. Package: `com.aspects.smp`. Main class: `com.aspects.smp.AspectsPlugin`.
- Ability activation trigger is **sneak + swap-hands** (unchanged from the Phase 1 hardening fix) — nothing in this plan touches `ActivationListener`.
- `TemporaryEffectManager` is **explicitly NOT built in this plan**. Earth doesn't need it (cooldown already exceeds buff duration) and Thunder doesn't need it either. It is deferred to the Batch B2 plan, where Blood is the first Aspect that actually needs to query "is a buff currently active" outside of the activation call itself.
- Thunder's dash computes its horizontal direction from **yaw only** via the new `DirectionUtil.horizontalFromYaw`, never from `Location.getDirection()` with pitch — a dash should move across the ground plane regardless of where the camera is aimed. This is the same reasoning (and the same underlying math, now shared) as Wind's jump from Batch A.
- `ParticleUtil.spawn` only supports particles that need no extra block/item data (e.g. `FLAME`, `CLOUD`, `SNOWFLAKE`, `CRIT`, `ELECTRIC_SPARK`) — never a `BLOCK`/`BLOCK_CRUMBLE`/`DUST`-style particle that requires a `BlockData`/`Particle.DustOptions` argument, since the existing `ParticleUtil` API doesn't support passing one. Do not introduce a particle that needs extra data in this plan.
- Never claim in-game behavior is verified unless it was actually tested on a running server. Every task in this plan is build-verified only except the two pure-logic extractions (`DirectionUtil`, `ThunderActiveAbility.effectiveDistance`), which get real unit tests. Live verification against a real Paper server is the controller's responsibility after all tasks are complete and reviewed — not any individual task's implementer's.

---

### Task 1: Extract `DirectionUtil` from `WindActiveAbility`

**Files:**
- Create: `src/main/java/com/aspects/smp/util/DirectionUtil.java`
- Create: `src/test/java/com/aspects/smp/util/DirectionUtilTest.java`
- Delete: `src/test/java/com/aspects/smp/ability/impl/WindActiveAbilityTest.java`
- Modify: `src/main/java/com/aspects/smp/ability/impl/WindActiveAbility.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `DirectionUtil.horizontalFromYaw(float yawDegrees) -> Vector`, a pure static method with no Bukkit server dependency. Consumed by `WindActiveAbility` (this task) and `ThunderActiveAbility` (Task 5).
- This is a pure relocation: the method's logic and its five test cases are unchanged, only their package/class location moves.

- [ ] **Step 1: Create `src/main/java/com/aspects/smp/util/DirectionUtil.java`**

```java
package com.aspects.smp.util;

import org.bukkit.util.Vector;

public final class DirectionUtil {
    private DirectionUtil() {}

    public static Vector horizontalFromYaw(float yawDegrees) {
        double yawRadians = Math.toRadians(yawDegrees);
        return new Vector(-Math.sin(yawRadians), 0, Math.cos(yawRadians));
    }
}
```

- [ ] **Step 2: Create `src/test/java/com/aspects/smp/util/DirectionUtilTest.java`**

```java
package com.aspects.smp.util;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectionUtilTest {

    private static final double DELTA = 1e-9;

    @Test
    void horizontalFromYaw_zeroYaw_pointsTowardPositiveZ() {
        Vector result = DirectionUtil.horizontalFromYaw(0f);
        assertEquals(0.0, result.getX(), DELTA);
        assertEquals(0.0, result.getY(), DELTA);
        assertEquals(1.0, result.getZ(), DELTA);
    }

    @Test
    void horizontalFromYaw_ninetyDegrees_pointsTowardNegativeX() {
        Vector result = DirectionUtil.horizontalFromYaw(90f);
        assertEquals(-1.0, result.getX(), DELTA);
        assertEquals(0.0, result.getY(), DELTA);
        assertEquals(0.0, result.getZ(), DELTA);
    }

    @Test
    void horizontalFromYaw_negativeNinetyDegrees_pointsTowardPositiveX() {
        Vector result = DirectionUtil.horizontalFromYaw(-90f);
        assertEquals(1.0, result.getX(), DELTA);
        assertEquals(0.0, result.getY(), DELTA);
        assertEquals(0.0, result.getZ(), DELTA);
    }

    @Test
    void horizontalFromYaw_oneEightyDegrees_pointsTowardNegativeZ() {
        Vector result = DirectionUtil.horizontalFromYaw(180f);
        assertEquals(0.0, result.getX(), DELTA);
        assertEquals(0.0, result.getY(), DELTA);
        assertEquals(-1.0, result.getZ(), DELTA);
    }

    @Test
    void horizontalFromYaw_alwaysUnitLength() {
        for (float yaw = -360f; yaw <= 360f; yaw += 17f) {
            Vector result = DirectionUtil.horizontalFromYaw(yaw);
            assertEquals(1.0, result.length(), DELTA, "yaw=" + yaw);
        }
    }
}
```

- [ ] **Step 3: Delete the old test file**

```bash
git rm src/test/java/com/aspects/smp/ability/impl/WindActiveAbilityTest.java
```

- [ ] **Step 4: Modify `src/main/java/com/aspects/smp/ability/impl/WindActiveAbility.java`**

Read the current file first. Replace its entire content with:

```java
package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.AbilityContext;
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.util.DirectionUtil;
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

        Vector horizontal = DirectionUtil.horizontalFromYaw(player.getLocation().getYaw());
        Vector impulse = horizontal.multiply(forwardVelocity).setY(upwardVelocity);
        player.setVelocity(impulse);

        SoundUtil.play(player, "entity.bat.takeoff", 1.0f, 1.0f, context.plugin().getLogger());
        ParticleUtil.spawn(player.getLocation(), "CLOUD", 20, context.plugin().getLogger());
        return true;
    }
}
```

The only changes from the current file: the `horizontalFromYaw` static method is removed, an import for `com.aspects.smp.util.DirectionUtil` is added, and the one call site now reads `DirectionUtil.horizontalFromYaw(...)` instead of the bare method name. Nothing else changes.

- [ ] **Step 5: Run the full test suite to verify the relocation is behavior-preserving**

Run: `mvn -q test`
Expected: all 36 pre-existing tests still pass (the 5 that were `WindActiveAbilityTest` now run as `DirectionUtilTest`, same assertions, same count).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/aspects/smp/util/DirectionUtil.java src/test/java/com/aspects/smp/util/DirectionUtilTest.java src/main/java/com/aspects/smp/ability/impl/WindActiveAbility.java
git commit -m "$(cat <<'EOF'
Extract DirectionUtil from WindActiveAbility for reuse by Thunder

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Refactor `AspectsPlugin.buildRegistry` into a factory-table pattern

**Files:**
- Modify: `src/main/java/com/aspects/smp/AspectsPlugin.java`

**Interfaces:**
- Consumes: `Ability`, `PassiveAbility` interfaces (Phase 1 Task 8); the four existing ability implementation pairs (Fire, Tide, Frost, Wind).
- Produces: two new `private static final Map<String, Supplier<...>>` fields (`PASSIVE_FACTORIES`, `ACTIVE_FACTORIES`) that Task 6 will extend with two more entries each. No change to `AspectsPlugin`'s public methods (`getConfigManager`, `getAspectManager`) or to `onEnable`/`onDisable`.
- This task is a **behavior-preserving refactor only** — the set of registered Aspects and the "not yet implemented" logging behavior must be identical before and after. No new Aspects are enabled in this task.

- [ ] **Step 1: Read the current `src/main/java/com/aspects/smp/AspectsPlugin.java`**

(Already read during planning. Your actual edit must be made against whatever the file currently contains; if it has drifted from what this plan expects, stop and report BLOCKED rather than guessing.)

- [ ] **Step 2: Replace the file's imports, add the two factory-table fields, and replace `buildRegistry`**

Add `import com.aspects.smp.ability.Ability;`, `import com.aspects.smp.ability.PassiveAbility;`, and `import java.util.function.Supplier;` to the existing import list (keep the existing imports, just add these three).

Add these two fields as the very first members of the class, before the existing instance fields:

```java
    private static final Map<String, Supplier<PassiveAbility>> PASSIVE_FACTORIES = Map.of(
            "fire", FirePassiveAbility::new,
            "tide", TidePassiveAbility::new,
            "frost", FrostPassiveAbility::new,
            "wind", WindPassiveAbility::new
    );
    private static final Map<String, Supplier<Ability>> ACTIVE_FACTORIES = Map.of(
            "fire", FireActiveAbility::new,
            "tide", TideActiveAbility::new,
            "frost", FrostActiveAbility::new,
            "wind", WindActiveAbility::new
    );
```

Replace the entire `buildRegistry` method body:

```java
    private AspectRegistry buildRegistry(Map<String, AspectDefinition> definitions) {
        AspectRegistry registry = new AspectRegistry();
        for (AspectDefinition definition : definitions.values()) {
            Supplier<PassiveAbility> passiveFactory = PASSIVE_FACTORIES.get(definition.id());
            Supplier<Ability> activeFactory = ACTIVE_FACTORIES.get(definition.id());
            if (passiveFactory == null || activeFactory == null) {
                getLogger().info("Aspect '" + definition.id() +
                        "' is defined in aspects.yml but not yet implemented in code; skipping registration.");
                continue;
            }
            registry.register(new Aspect(definition, passiveFactory.get(), activeFactory.get()));
        }
        return registry;
    }
```

Do not change `onEnable`, `onDisable`, `getConfigManager`, or `getAspectManager` at all.

- [ ] **Step 3: Full build and test**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`, all 36 pre-existing tests pass (no new tests in this task — this is a refactor of already-integration-tested code, same precedent as every other `AspectsPlugin` change), and `target/aspects-1.0.0-SNAPSHOT.jar` exists.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/aspects/smp/AspectsPlugin.java
git commit -m "$(cat <<'EOF'
Refactor buildRegistry into a factory-table pattern

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Enable Earth and Thunder in aspects.yml

**Files:**
- Modify: `src/main/resources/aspects.yml`

**Interfaces:**
- Consumes: nothing new — `AspectsConfigParser.parseAspects` (Phase 1, unit-tested) reads this file unchanged.
- Produces: `aspects.yml` entries for `earth` and `thunder` with `enabled: true` and real settings, consumed by `AspectsPlugin.buildRegistry`'s factory-table lookups in Task 6.

- [ ] **Step 1: Replace the `thunder:` and `earth:` blocks in `src/main/resources/aspects.yml`**

Find the existing `thunder:` block (currently `enabled: false`, no `passive`/`active` sections) and replace it with:

```yaml
  thunder:
    enabled: true
    display-name: "<yellow>Thunder</yellow>"
    description: "Speed, mobility, and explosive engagement."
    rarity: EPIC
    weight: 10
    passive:
      speed-boost: true
    active:
      cooldown-seconds: 18
      max-distance: 7.0
      velocity-per-block: 0.6
      vertical-boost: 0.15
      wall-safety-margin: 0.5
```

Find the existing `earth:` block (currently `enabled: false`, no `passive`/`active` sections) and replace it with:

```yaml
  earth:
    enabled: true
    display-name: "<dark_green>Earth</dark_green>"
    description: "Defense, stability, and holding ground."
    rarity: RARE
    weight: 20
    passive:
      resistance-boost: true
    active:
      cooldown-seconds: 30
      resistance-duration-ticks: 60
      resistance-amplifier: 1
```

Leave `void` and `blood` completely unchanged (still `enabled: false`, `weight: 0`, "Not yet implemented (planned for Phase 2)." description).

- [ ] **Step 2: Update the file's header comment**

Find:

```yaml
# fire, tide, frost, and wind are implemented (Phase 1 and Phase 2 Batch A).
# The remaining four are listed here with their final rarity/description so
# the file's shape is correct, but their abilities are NOT implemented yet —
# they ship disabled until a later phase adds their code.
```

Replace with:

```yaml
# fire, tide, frost, wind, earth, and thunder are implemented (Phase 1 and
# Phase 2 Batches A and B1). void and blood are listed here with their final
# rarity/description so the file's shape is correct, but their abilities are
# NOT implemented yet — they ship disabled until Batch B2 adds their code.
```

- [ ] **Step 3: Build to verify the resource is valid and no existing tests regress**

Run: `mvn -q test`
Expected: all 36 existing tests still pass (this resource file isn't read directly by any test — they use inline YAML via `StringReader` — this is a regression safety net).

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/aspects.yml
git commit -m "$(cat <<'EOF'
Enable Earth and Thunder in aspects.yml with real settings

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Earth Aspect

**Files:**
- Create: `src/main/java/com/aspects/smp/ability/impl/EarthPassiveAbility.java`
- Create: `src/main/java/com/aspects/smp/ability/impl/EarthActiveAbility.java`

**Interfaces:**
- Consumes: `AbilityContext`, `PassiveAbility`, `Ability` (Phase 1 Task 8); `SoundUtil.play`, `ParticleUtil.spawn` (Phase 1 Task 10); `AspectDefinition.getBoolean/getInt` (Phase 1 Task 4).
- Produces: `EarthPassiveAbility`, `EarthActiveAbility`.
- **Manual verification only** (no automated test), same reasoning as every other ability implementation — this is pure potion-effect application, no logic worth extracting.

- [ ] **Step 1: Create `src/main/java/com/aspects/smp/ability/impl/EarthPassiveAbility.java`**

```java
package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.PassiveAbility;
import com.aspects.smp.aspect.AspectDefinition;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class EarthPassiveAbility implements PassiveAbility {
    @Override
    public void onEquip(Player player, AspectDefinition definition) {
        if (!definition.getBoolean("passive.resistance-boost", true)) {
            return;
        }
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 0, true, false, false));
    }

    @Override
    public void onUnequip(Player player) {
        player.removePotionEffect(PotionEffectType.RESISTANCE);
    }
}
```

- [ ] **Step 2: Create `src/main/java/com/aspects/smp/ability/impl/EarthActiveAbility.java`**

```java
package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.AbilityContext;
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.util.ParticleUtil;
import com.aspects.smp.util.SoundUtil;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class EarthActiveAbility implements Ability {
    @Override
    public boolean activate(AbilityContext context) {
        Player player = context.player();
        AspectDefinition definition = context.aspect().definition();

        int duration = definition.getInt("active.resistance-duration-ticks", 60);
        int amplifier = definition.getInt("active.resistance-amplifier", 1);

        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, amplifier, true, true, true));

        SoundUtil.play(player, "block.stone.break", 1.0f, 0.8f, context.plugin().getLogger());
        ParticleUtil.spawn(player.getLocation(), "CRIT", 20, context.plugin().getLogger());
        return true;
    }
}
```

- [ ] **Step 3: Build to verify compilation**

Run: `mvn -q package -DskipTests`
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Run the full test suite to confirm no regressions**

Run: `mvn -q test`
Expected: all 36 pre-existing tests still pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/aspects/smp/ability/impl/EarthPassiveAbility.java src/main/java/com/aspects/smp/ability/impl/EarthActiveAbility.java
git commit -m "$(cat <<'EOF'
Implement the Earth Aspect (resistance passive, Earthguard active)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Thunder Aspect

**Files:**
- Create: `src/main/java/com/aspects/smp/ability/impl/ThunderPassiveAbility.java`
- Create: `src/main/java/com/aspects/smp/ability/impl/ThunderActiveAbility.java`
- Test: `src/test/java/com/aspects/smp/ability/impl/ThunderActiveAbilityTest.java`

**Interfaces:**
- Consumes: `AbilityContext`, `PassiveAbility`, `Ability` (Phase 1 Task 8); `SoundUtil.play`, `ParticleUtil.spawn` (Phase 1 Task 10); `AspectDefinition.getDouble/getBoolean` (Phase 1 Task 4); `DirectionUtil.horizontalFromYaw` (Task 1 of this plan).
- Produces: `ThunderPassiveAbility`, `ThunderActiveAbility` (including a package-visible pure static method `effectiveDistance(Double, double, double) -> double`, unit-tested).
- `ThunderPassiveAbility` and the Bukkit-API parts of `ThunderActiveAbility.activate` are **manual verification only**. `effectiveDistance` is a genuine, pure, unit-tested function — this is the one piece of new logic in Batch B1 worth testing directly, per the design doc.

- [ ] **Step 1: Create `src/main/java/com/aspects/smp/ability/impl/ThunderPassiveAbility.java`**

```java
package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.PassiveAbility;
import com.aspects.smp.aspect.AspectDefinition;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class ThunderPassiveAbility implements PassiveAbility {
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

- [ ] **Step 2: Write the failing test for `effectiveDistance`**

```java
package com.aspects.smp.ability.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThunderActiveAbilityTest {

    private static final double DELTA = 1e-9;

    @Test
    void effectiveDistance_noHit_returnsMaxDistance() {
        assertEquals(7.0, ThunderActiveAbility.effectiveDistance(null, 7.0, 0.5), DELTA);
    }

    @Test
    void effectiveDistance_hitFarAway_clampsToMaxDistance() {
        assertEquals(7.0, ThunderActiveAbility.effectiveDistance(100.0, 7.0, 0.5), DELTA);
    }

    @Test
    void effectiveDistance_hitWithinRange_subtractsSafetyMargin() {
        assertEquals(2.5, ThunderActiveAbility.effectiveDistance(3.0, 7.0, 0.5), DELTA);
    }

    @Test
    void effectiveDistance_hitVeryClose_clampsToZeroNotNegative() {
        assertEquals(0.0, ThunderActiveAbility.effectiveDistance(0.2, 7.0, 0.5), DELTA);
    }

    @Test
    void effectiveDistance_hitExactlyAtMargin_returnsZero() {
        assertEquals(0.0, ThunderActiveAbility.effectiveDistance(0.5, 7.0, 0.5), DELTA);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q test -Dtest=ThunderActiveAbilityTest`
Expected: FAIL — `ThunderActiveAbility` does not exist.

- [ ] **Step 4: Create `src/main/java/com/aspects/smp/ability/impl/ThunderActiveAbility.java`**

```java
package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.AbilityContext;
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.util.DirectionUtil;
import com.aspects.smp.util.ParticleUtil;
import com.aspects.smp.util.SoundUtil;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class ThunderActiveAbility implements Ability {
    @Override
    public boolean activate(AbilityContext context) {
        Player player = context.player();
        AspectDefinition definition = context.aspect().definition();

        double maxDistance = definition.getDouble("active.max-distance", 7.0);
        double velocityPerBlock = definition.getDouble("active.velocity-per-block", 0.6);
        double verticalBoost = definition.getDouble("active.vertical-boost", 0.15);
        double safetyMargin = definition.getDouble("active.wall-safety-margin", 0.5);

        Vector horizontal = DirectionUtil.horizontalFromYaw(player.getLocation().getYaw());
        Location eyeLocation = player.getEyeLocation();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(
                eyeLocation, horizontal, maxDistance, FluidCollisionMode.NEVER, true);
        Double hitDistance = hit == null ? null : eyeLocation.toVector().distance(hit.getHitPosition());

        double distance = effectiveDistance(hitDistance, maxDistance, safetyMargin);

        Vector impulse = horizontal.multiply(distance * velocityPerBlock).setY(verticalBoost);
        player.setVelocity(impulse);

        SoundUtil.play(player, "entity.lightning_bolt.impact", 1.0f, 1.2f, context.plugin().getLogger());
        ParticleUtil.spawn(player.getLocation(), "ELECTRIC_SPARK", 25, context.plugin().getLogger());
        return true;
    }

    static double effectiveDistance(Double hitDistance, double maxDistance, double safetyMargin) {
        if (hitDistance == null) {
            return maxDistance;
        }
        return Math.max(0.0, Math.min(maxDistance, hitDistance - safetyMargin));
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q test -Dtest=ThunderActiveAbilityTest`
Expected: PASS (5 tests).

- [ ] **Step 6: Run the full test suite to confirm no regressions**

Run: `mvn -q test`
Expected: all 36 pre-existing tests plus these 5 new ones pass (41 total).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/aspects/smp/ability/impl/ThunderPassiveAbility.java src/main/java/com/aspects/smp/ability/impl/ThunderActiveAbility.java src/test/java/com/aspects/smp/ability/impl/ThunderActiveAbilityTest.java
git commit -m "$(cat <<'EOF'
Implement the Thunder Aspect (speed passive, raycast-safe Lightning Dash)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Wire Earth and Thunder into the factory tables, full build

**Files:**
- Modify: `src/main/java/com/aspects/smp/AspectsPlugin.java`

**Interfaces:**
- Consumes: everything from Tasks 1-5, plus Task 2's factory-table structure.
- Produces: nothing new for later tasks — this is the integration point for Batch B1.

- [ ] **Step 1: Read the current `src/main/java/com/aspects/smp/AspectsPlugin.java`**

(It should now have the factory-table structure from Task 2. If it doesn't, or looks different from what Task 2 produced, stop and report BLOCKED rather than guessing.)

- [ ] **Step 2: Add two imports**

Add these four import lines alongside the existing `com.aspects.smp.ability.impl.*` imports (keep the existing alphabetical grouping):

```java
import com.aspects.smp.ability.impl.EarthActiveAbility;
import com.aspects.smp.ability.impl.EarthPassiveAbility;
import com.aspects.smp.ability.impl.ThunderActiveAbility;
import com.aspects.smp.ability.impl.ThunderPassiveAbility;
```

- [ ] **Step 3: Extend both factory-table fields**

Find:

```java
    private static final Map<String, Supplier<PassiveAbility>> PASSIVE_FACTORIES = Map.of(
            "fire", FirePassiveAbility::new,
            "tide", TidePassiveAbility::new,
            "frost", FrostPassiveAbility::new,
            "wind", WindPassiveAbility::new
    );
    private static final Map<String, Supplier<Ability>> ACTIVE_FACTORIES = Map.of(
            "fire", FireActiveAbility::new,
            "tide", TideActiveAbility::new,
            "frost", FrostActiveAbility::new,
            "wind", WindActiveAbility::new
    );
```

Replace with:

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

Do not change anything else in the file — `buildRegistry`'s loop body, `onEnable`, `onDisable`, and the two public getters all stay exactly as Task 2 left them; they need no changes since the loop is already generic over whatever is in the maps.

- [ ] **Step 4: Full build and test**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`, all 41 unit tests pass, and `target/aspects-1.0.0-SNAPSHOT.jar` exists.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/aspects/smp/AspectsPlugin.java
git commit -m "$(cat <<'EOF'
Wire Earth and Thunder into the factory tables

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 6: Note for the controller, not this task's implementer**

Live verification against a running Paper 1.21.11 server (confirming: Earthguard applies Resistance II to self for 3 seconds and goes on a 30-second cooldown; Thunder's dash launches the player forward roughly as configured on open ground; the dash is visibly shorter or does nothing when a wall is immediately in front of the player, rather than launching through it; `/aspects admin set <player> earth` and `thunder` work via the existing commands with no changes needed there) is the controller's responsibility after this task's review passes, the same way Phase 1 Task 15's Step 4 and Batch A Task 5's Step 7 were — do not attempt this as the task implementer; there is no running server in that environment.

---

## Post-implementation hardening (final whole-branch review)

After Task 6, a final whole-branch review (most-capable-model, base `c365091`..`b964846`) found two Important-severity issues, both of which turned out to be judgment calls about live-server behavior that needed empirical measurement, not just code reading — exactly the kind of thing per-task review can reason about but can't settle alone.

1. **Thunder's default `velocity-per-block` (0.6) was likely miscalibrated by roughly 2x.** The reviewer's physics reasoning (typical Minecraft horizontal drag/friction applied to the resulting ~4.2 blocks/tick initial impulse at the default 7-block max distance) predicted a real dash distance of ~15-16 blocks against a spec target of "~6-8 blocks on open ground" — which would also mean the raycast-and-clamp wall safety only covers a fraction of the actual travel path. This was verified directly on a live Paper 1.21.11 server (Docker): a bot's dash was measured via the server's own authoritative entity NBT (`Motion`), not client-reported position, since a scripted test client doesn't reliably simulate incoming server-pushed velocity for its own avatar (the same limitation documented in Batch A's hardening section for Wind Leap). The default produced a sampled horizontal speed of ~1.9 blocks/tick just 2 ticks after activation — already significantly decayed from an even higher initial value, and far above normal player movement speeds — corroborating the reviewer's estimate. The default was halved to `0.3`; re-measuring confirmed a proportional drop (~0.52 blocks/tick at the same sample point), consistent with the fix actually taking effect.
2. **The mid-batch Earthguard fix (documented in the Task 4 section above) was built on an incorrect premise.** The reviewer correctly questioned whether Bukkit/vanilla on Paper 1.21 actually replaces same-type potion effects outright (the premise behind scheduling a delayed passive re-application), noting that modern vanilla has "hidden effect" semantics that may restore a weaker/longer effect automatically when a stronger/shorter one of the same type expires. This was verified directly: reading the entity's `active_effects` NBT on a live server showed the timed Resistance II buff carrying a nested `hidden_effect` entry (the tucked-away Resistance I), and a poll taken after the 3-second duration had elapsed showed Resistance I already restored — before the scheduled task could plausibly have fired. The scheduled re-application was therefore a harmless no-op, not a necessary fix. It was removed, and the code comment was corrected to describe the actual, verified behavior rather than the original (wrong) assumption. This matters beyond Earth: any future aspect that layers potion effects (including however Batch B2's Blood ends up applying its damage-related effects, if it uses real potion effects at all) should rely on this now-verified vanilla behavior rather than re-deriving it from scratch.

Both fixes were applied and empirically re-verified by the controller directly against a live server rather than through another review pass — live measurement is a stronger form of evidence here than a text-based code re-review would have been, since the entire question in both cases was "what does the live platform actually do," not "does this code match the plan."

The remaining Minor findings (the two factory tables in `AspectsPlugin` can in principle drift apart with a misleading diagnostic; Thunder's raycast only checks eye height, missing lower obstacles; a dash clamped to zero distance by a nearby wall still consumes the full cooldown; no validation rejects nonsensical negative config values; Earthguard's now-removed reapply task would have been untracked if kept) are left for the user to triage before or during Batch B2; none block calling Batch B1 complete.

