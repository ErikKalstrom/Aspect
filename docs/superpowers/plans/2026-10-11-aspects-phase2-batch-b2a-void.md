# Aspects Plugin — Phase 2 Batch B2a (Void) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Void Aspect (partial fall-damage reduction passive + a safe-teleport "Void Step" active) to the existing framework. Blood, the other half of Batch B2, is a separate plan (`2026-10-11-aspects-phase2-batch-b2b-blood.md`) executed after this one.

**Architecture:** See `docs/superpowers/specs/2026-10-11-aspects-phase2-batch-b2-design.md` for full rationale. In short: Void's passive extends the existing `PlayerDamageListener` (built in Batch B1 for Frost) with a second cause-based check; its active ability searches for a safe teleport destination by scanning candidate points from far to near, with the scan-order logic extracted as a pure, unit-tested function exactly like Thunder's wall-clamp math in Batch B1.

**Tech Stack:** Java 21, Maven, Paper API `1.21.11-R0.1-SNAPSHOT`, Adventure/MiniMessage, SQLite (unchanged, not touched by this plan).

## Global Constraints

- Minecraft/Paper target: **1.21.11**. Paper API coordinate: `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`, scope `provided`. Unchanged from Phase 1.
- Java: **21**. Package: `com.aspects.smp`. Main class: `com.aspects.smp.AspectsPlugin`.
- Ability activation trigger is **sneak + swap-hands** (unchanged). Nothing in this plan touches `ActivationListener`.
- **Every `PlayerDamageListener` check must test the Aspect's id FIRST, before reading any config flag.** This is not a style preference — Batch B1's final review found a live bug where `getBoolean(key, defaultTrue)` was checked before confirming the Aspect id, silently granting Frost's freeze immunity to every other Aspect, because the default-true fallback applied whenever the key was simply absent from a *different* Aspect's settings. `fallDamageMultiplier` in this plan follows the same id-first pattern `cancelsFreezeDamage` was fixed to use.
- Void's teleport direction comes from `DirectionUtil.horizontalFromYaw(player.getLocation().getYaw())` (yaw only, not pitch) — the same existing, tested utility Wind and Thunder already use. Do not reimplement it.
- `pickSafeDistance` is deliberately a pure, package-visible static method taking an `IntPredicate` so the scan-order and fail-to-zero logic is unit-testable without a live `World`. The actual per-point safety check (`isSafeDestination`) is necessarily Bukkit-API-dependent and is manual-verification-only.
- `TemporaryEffectManager` is **not built in this plan** — Void doesn't need it. It is built in the Blood plan (`2026-10-11-aspects-phase2-batch-b2b-blood.md`), which follows this one.
- Never claim in-game behavior is verified unless it was actually tested on a running server. Live verification is the controller's responsibility after all tasks are complete and reviewed — not any individual task's implementer's.

---

### Task 1: Extend PlayerDamageListener for Void's fall-damage reduction

**Files:**
- Modify: `src/main/java/com/aspects/smp/listener/PlayerDamageListener.java`
- Modify: `src/test/java/com/aspects/smp/listener/PlayerDamageListenerTest.java`

**Interfaces:**
- Consumes: `Aspect`, `AspectDefinition.getBoolean/getInt` (existing, Phase 1/Batch A).
- Produces: a new package-visible static `PlayerDamageListener.fallDamageMultiplier(Aspect) -> double` (returns `1.0` for "no reduction applies", or a value in `[0.0, 1.0)` otherwise), consumed by `onDamage`'s own body and unit-tested directly, same pattern as the existing `cancelsFreezeDamage`.
- `onDamage`'s body is restructured so the Aspect lookup happens once, before branching on `event.getCause()`, rather than the single-cause early-return the method currently has. `cancelsFreezeDamage`'s own signature and behavior are unchanged — the 5 existing tests for it must still pass unmodified.

- [ ] **Step 1: Read the current `PlayerDamageListener.java` and `PlayerDamageListenerTest.java`**

(Already read during planning — shown below for reference. Your actual edit must be made against whatever these files currently contain; if they've drifted from this plan's expectation, stop and report BLOCKED rather than guessing.)

- [ ] **Step 2: Write the 5 new failing tests — append to `PlayerDamageListenerTest.java`**

Add `import static org.junit.jupiter.api.Assertions.assertEquals;` to the existing imports (keep `assertFalse`/`assertTrue` too — they're still used by the existing tests). Then add these 5 methods inside the existing `PlayerDamageListenerTest` class, after the existing `cancelsFreezeDamage_*` tests:

```java
    @Test
    void fallDamageMultiplier_returnsHalf_forVoidWithNoExplicitFlag() {
        Aspect voidAspect = aspect("void", Map.of());

        assertEquals(0.5, PlayerDamageListener.fallDamageMultiplier(voidAspect), 1e-9);
    }

    @Test
    void fallDamageMultiplier_returnsOne_forVoidWithFlagDisabled() {
        Aspect voidAspect = aspect("void", Map.of("passive.fall-damage-reduction", false));

        assertEquals(1.0, PlayerDamageListener.fallDamageMultiplier(voidAspect), 1e-9);
    }

    @Test
    void fallDamageMultiplier_respectsCustomReductionPercent() {
        Aspect voidAspect = aspect("void",
                Map.of("passive.fall-damage-reduction", true, "passive.reduction-percent", 30));

        assertEquals(0.7, PlayerDamageListener.fallDamageMultiplier(voidAspect), 1e-9);
    }

    @Test
    void fallDamageMultiplier_returnsOne_forFireEvenWithEmptySettings() {
        Aspect fire = aspect("fire", Map.of());

        assertEquals(1.0, PlayerDamageListener.fallDamageMultiplier(fire), 1e-9);
    }

    @Test
    void fallDamageMultiplier_returnsOne_forFrostEvenIfUnrelatedFlagEnabled() {
        Aspect frost = aspect("frost", Map.of("passive.freeze-immunity", true));

        assertEquals(1.0, PlayerDamageListener.fallDamageMultiplier(frost), 1e-9);
    }
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -q test -Dtest=PlayerDamageListenerTest`
Expected: FAIL — `PlayerDamageListener.fallDamageMultiplier` does not exist. The 5 existing `cancelsFreezeDamage_*` tests should still compile and pass; only the 5 new ones fail.

- [ ] **Step 4: Replace `PlayerDamageListener.java`'s content**

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
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Aspect aspect = plugin.getAspectManager().getAspect(player.getUniqueId()).orElse(null);
        if (aspect == null) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FREEZE) {
            if (cancelsFreezeDamage(aspect)) {
                event.setCancelled(true);
            }
        } else if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            double multiplier = fallDamageMultiplier(aspect);
            if (multiplier != 1.0) {
                event.setDamage(event.getDamage() * multiplier);
            }
        }
    }

    static boolean cancelsFreezeDamage(Aspect aspect) {
        return aspect.id().equals("frost") && aspect.definition().getBoolean("passive.freeze-immunity", true);
    }

    static double fallDamageMultiplier(Aspect aspect) {
        if (!aspect.id().equals("void")) {
            return 1.0;
        }
        if (!aspect.definition().getBoolean("passive.fall-damage-reduction", true)) {
            return 1.0;
        }
        int reductionPercent = aspect.definition().getInt("passive.reduction-percent", 50);
        return 1.0 - (reductionPercent / 100.0);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -q test -Dtest=PlayerDamageListenerTest`
Expected: PASS (10 tests: 5 existing `cancelsFreezeDamage_*` plus 5 new `fallDamageMultiplier_*`).

- [ ] **Step 6: Run the full test suite to confirm no regressions**

Run: `mvn -q test`
Expected: all pre-existing tests still pass (41 before this task, 46 after — 36 from Phase 1/Batch A, 5 from Batch B1's `ThunderActiveAbilityTest`, now plus these 5 new ones; the exact pre-task baseline is 41, confirm your actual total is 46).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/aspects/smp/listener/PlayerDamageListener.java src/test/java/com/aspects/smp/listener/PlayerDamageListenerTest.java
git commit -m "$(cat <<'EOF'
Extend PlayerDamageListener for Void's fall-damage reduction

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Enable Void in aspects.yml

**Files:**
- Modify: `src/main/resources/aspects.yml`

**Interfaces:**
- Consumes: nothing new — `AspectsConfigParser.parseAspects` (Phase 1, unit-tested) reads this file unchanged.
- Produces: an `aspects.yml` entry for `void` with `enabled: true` and real settings, consumed by `AspectsPlugin.buildRegistry`'s factory-table lookups in Task 5.

- [ ] **Step 1: Replace the `void:` block in `src/main/resources/aspects.yml`**

Find the existing `void:` block (currently `enabled: false`, no `passive`/`active` sections) and replace it with:

```yaml
  void:
    enabled: true
    display-name: "<dark_purple>Void</dark_purple>"
    description: "Teleportation, deception, and positioning."
    rarity: LEGENDARY
    weight: 5
    passive:
      fall-damage-reduction: true
      reduction-percent: 50
    active:
      cooldown-seconds: 22
      max-distance: 7.0
```

Leave `blood` completely unchanged (still `enabled: false`, `weight: 0`, "Not yet implemented (planned for Phase 2)." description) — it is Batch B2b's job.

- [ ] **Step 2: Update the file's header comment**

Find:

```yaml
# fire, tide, frost, wind, earth, and thunder are implemented (Phase 1 and
# Phase 2 Batches A and B1). void and blood are listed here with their final
# rarity/description so the file's shape is correct, but their abilities are
# NOT implemented yet — they ship disabled until Batch B2 adds their code.
```

Replace with:

```yaml
# fire, tide, frost, wind, earth, thunder, and void are implemented (Phase 1,
# Phase 2 Batches A and B1, and Batch B2a). blood is listed here with its
# final rarity/description so the file's shape is correct, but its abilities
# are NOT implemented yet — it ships disabled until Batch B2b adds its code.
```

- [ ] **Step 3: Build to verify the resource is valid and no existing tests regress**

Run: `mvn -q test`
Expected: all 46 existing tests still pass (this resource file isn't read directly by any test — they use inline YAML via `StringReader` — this is a regression safety net).

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/aspects.yml
git commit -m "$(cat <<'EOF'
Enable Void in aspects.yml with real settings

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Void's passive ability

**Files:**
- Create: `src/main/java/com/aspects/smp/ability/impl/VoidPassiveAbility.java`

**Interfaces:**
- Consumes: `PassiveAbility` (Phase 1 Task 8).
- Produces: `VoidPassiveAbility`, a no-op implementation — Void's fall-damage reduction is enforced live by `PlayerDamageListener` (Task 1), checking the player's current Aspect on each `FALL` damage event, exactly like `FrostPassiveAbility`'s precedent for freeze immunity. There is no potion effect or other per-player state to apply or remove.
- **Manual verification only** is not even applicable here in the usual sense — there is nothing for this class to do at runtime beyond satisfying the interface. The real behavior is verified via Task 1's unit tests plus live verification of the full ability.

- [ ] **Step 1: Create `src/main/java/com/aspects/smp/ability/impl/VoidPassiveAbility.java`**

```java
package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.PassiveAbility;
import com.aspects.smp.aspect.AspectDefinition;
import org.bukkit.entity.Player;

public final class VoidPassiveAbility implements PassiveAbility {
    @Override
    public void onEquip(Player player, AspectDefinition definition) {
        // Fall-damage reduction has no vanilla potion-effect equivalent. It is enforced
        // live by PlayerDamageListener checking the player's current Aspect on each FALL
        // damage event, so there is no state to apply here.
    }

    @Override
    public void onUnequip(Player player) {
        // No state to remove — see onEquip.
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `mvn -q package -DskipTests`
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/aspects/smp/ability/impl/VoidPassiveAbility.java
git commit -m "$(cat <<'EOF'
Add VoidPassiveAbility (enforcement lives in PlayerDamageListener)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Void's active ability (safe-teleport search)

**Files:**
- Create: `src/main/java/com/aspects/smp/ability/impl/VoidActiveAbility.java`
- Test: `src/test/java/com/aspects/smp/ability/impl/VoidActiveAbilityTest.java`

**Interfaces:**
- Consumes: `AbilityContext`, `Ability` (Phase 1 Task 8); `SoundUtil.play`, `ParticleUtil.spawn` (Phase 1 Task 10); `AspectDefinition.getDouble` (Phase 1 Task 4); `DirectionUtil.horizontalFromYaw` (Batch B1 Task 1).
- Produces: `VoidActiveAbility`, including a package-visible pure static method `pickSafeDistance(int maxDistance, IntPredicate isSafeAtDistance) -> int`, unit-tested directly.
- `activate(...)`'s live Bukkit-API parts (the actual block-safety check, the teleport, particles, sound) are manual-verification-only. `pickSafeDistance` is the one piece of new logic worth testing directly, per the design doc — exactly Thunder's `effectiveDistance` pattern from Batch B1.

- [ ] **Step 1: Write the failing test for `pickSafeDistance`**

```java
package com.aspects.smp.ability.impl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VoidActiveAbilityTest {

    @Test
    void pickSafeDistance_returnsMaxDistance_whenFarthestIsSafe() {
        assertEquals(7, VoidActiveAbility.pickSafeDistance(7, distance -> distance == 7));
    }

    @Test
    void pickSafeDistance_scansFromFarToNear_returnsFirstSafeOne() {
        assertEquals(4, VoidActiveAbility.pickSafeDistance(7, distance -> distance <= 4));
    }

    @Test
    void pickSafeDistance_returnsZero_whenNothingIsSafe() {
        assertEquals(0, VoidActiveAbility.pickSafeDistance(7, distance -> false));
    }

    @Test
    void pickSafeDistance_checksExactlyOneThroughMaxDistance_noOffByOne() {
        List<Integer> checked = new ArrayList<>();
        VoidActiveAbility.pickSafeDistance(3, distance -> {
            checked.add(distance);
            return false;
        });
        assertEquals(List.of(3, 2, 1), checked);
    }

    @Test
    void pickSafeDistance_returnsOne_whenOnlyClosestIsSafe() {
        assertEquals(1, VoidActiveAbility.pickSafeDistance(5, distance -> distance == 1));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=VoidActiveAbilityTest`
Expected: FAIL — `VoidActiveAbility` does not exist.

- [ ] **Step 3: Create `src/main/java/com/aspects/smp/ability/impl/VoidActiveAbility.java`**

```java
package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.AbilityContext;
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.util.DirectionUtil;
import com.aspects.smp.util.ParticleUtil;
import com.aspects.smp.util.SoundUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.function.IntPredicate;

public final class VoidActiveAbility implements Ability {
    @Override
    public boolean activate(AbilityContext context) {
        Player player = context.player();
        AspectDefinition definition = context.aspect().definition();

        int maxDistance = (int) definition.getDouble("active.max-distance", 7.0);

        Location origin = player.getLocation();
        World world = player.getWorld();
        Vector horizontal = DirectionUtil.horizontalFromYaw(origin.getYaw());

        IntPredicate isSafeAtDistance = distance -> isSafeDestination(world, origin, horizontal, distance);
        int safeDistance = pickSafeDistance(maxDistance, isSafeAtDistance);
        if (safeDistance == 0) {
            return false;
        }

        Location destination = origin.clone().add(horizontal.clone().multiply(safeDistance));
        destination.setYaw(origin.getYaw());
        destination.setPitch(origin.getPitch());

        ParticleUtil.spawn(origin, "PORTAL", 30, context.plugin().getLogger());
        player.teleport(destination);
        ParticleUtil.spawn(destination, "PORTAL", 30, context.plugin().getLogger());
        SoundUtil.play(player, "entity.enderman.teleport", 1.0f, 1.0f, context.plugin().getLogger());
        return true;
    }

    static int pickSafeDistance(int maxDistance, IntPredicate isSafeAtDistance) {
        for (int distance = maxDistance; distance >= 1; distance--) {
            if (isSafeAtDistance.test(distance)) {
                return distance;
            }
        }
        return 0;
    }

    private boolean isSafeDestination(World world, Location origin, Vector horizontal, int distance) {
        Location point = origin.clone().add(horizontal.clone().multiply(distance));
        if (!world.getWorldBorder().isInside(point)) {
            return false;
        }
        int y = point.getBlockY();
        if (y < world.getMinHeight() || y + 1 > world.getMaxHeight()) {
            return false;
        }
        Material feet = point.getBlock().getType();
        Material head = point.clone().add(0, 1, 0).getBlock().getType();
        Material floor = point.clone().add(0, -1, 0).getBlock().getType();
        return !feet.isSolid() && !head.isSolid() && feet != Material.LAVA && head != Material.LAVA
                && floor != Material.LAVA;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=VoidActiveAbilityTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Run the full test suite to confirm no regressions**

Run: `mvn -q test`
Expected: 51 tests total (46 before this task, plus these 5 new ones).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/aspects/smp/ability/impl/VoidActiveAbility.java src/test/java/com/aspects/smp/ability/impl/VoidActiveAbilityTest.java
git commit -m "$(cat <<'EOF'
Implement Void's active ability (safe-teleport Void Step)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Wire Void into the factory tables, full build

**Files:**
- Modify: `src/main/java/com/aspects/smp/AspectsPlugin.java`

**Interfaces:**
- Consumes: everything from Tasks 1-4, plus the existing factory-table structure (Batch B1 Task 2).
- Produces: nothing new for later tasks — this is the integration point for Batch B2a.

- [ ] **Step 1: Read the current `src/main/java/com/aspects/smp/AspectsPlugin.java`**

(It should have the factory-table structure from Batch B1, now with 6 entries each for fire/tide/frost/wind/earth/thunder. If it looks different from what this plan expects, stop and report BLOCKED rather than guessing.)

- [ ] **Step 2: Add two imports**

Add these two import lines alongside the existing `com.aspects.smp.ability.impl.*` imports, in alphabetical position (between the `Tide*` and `Wind*` imports):

```java
import com.aspects.smp.ability.impl.VoidActiveAbility;
import com.aspects.smp.ability.impl.VoidPassiveAbility;
```

- [ ] **Step 3: Extend both factory-table fields**

Find the two `Map.of(...)` fields (`PASSIVE_FACTORIES`, `ACTIVE_FACTORIES`). Add one more entry to each, right after the `"thunder"` entry:

```java
            "thunder", ThunderPassiveAbility::new,
            "void", VoidPassiveAbility::new
```

and

```java
            "thunder", ThunderActiveAbility::new,
            "void", VoidActiveAbility::new
```

(Each map now has 7 entries total: fire, tide, frost, wind, earth, thunder, void.)

Do not change anything else in the file — `buildRegistry`'s loop body, `onEnable`, `onDisable`, and the two public getters all stay exactly as they are; the loop is already generic over whatever is in the maps.

- [ ] **Step 4: Full build and test**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`, all 51 unit tests pass, and `target/aspects-1.0.0-SNAPSHOT.jar` exists.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/aspects/smp/AspectsPlugin.java
git commit -m "$(cat <<'EOF'
Wire Void into the factory tables

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 6: Note for the controller, not this task's implementer**

Live verification against a running Paper 1.21.11 server (confirming: falling with Void equipped takes roughly half damage, not zero and not full; Void Step teleports the player forward on open ground and visibly refuses to teleport through/into a wall, instead landing short of it or failing entirely if nothing is safe; `/aspects admin set <player> void` works via the existing command with no changes needed there) is the controller's responsibility after this task's review passes, the same way every prior phase's final wiring task was — do not attempt this as the task implementer; there is no running server in that environment.
