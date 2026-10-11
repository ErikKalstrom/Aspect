# Aspects Plugin — Phase 2 Batch B2b (Blood) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the Blood Aspect (Epic rarity): a passive +10% outgoing melee damage bonus ("Bloodlust") and an active health-sacrifice ability ("Blood Pact") that grants an additional, temporary +20% melee damage bonus. This is the last of the eight functional-spec Aspects — Phase 2 is complete once this plan ships.

**Architecture:** A new `TemporaryEffectManager` (in-memory, no persistence, clock-injectable like `AbilityCooldownManager`) tracks whether the "blood-pact" buff is currently active per player. `PlayerDamageListener` gains a fourth handler, on `EntityDamageByEntityEvent`, that applies Blood's passive and (if active) buff bonuses to the damager's outgoing damage — following the mandatory id-check-first pattern. A new `PlayerDeathListener` clears the buff tracker on death. `BloodPassiveAbility` is a no-op (enforcement lives in the listener, matching `FrostPassiveAbility`/`VoidPassiveAbility`). `BloodActiveAbility` checks a health-affordability precondition (must not be able to kill the caster) before sacrificing health and starting the buff.

**Tech Stack:** Java 21, Paper API `1.21.11-R0.1-SNAPSHOT`, Maven, JUnit 5.

## Global Constraints

- Every commit ends with exactly: `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>` — regardless of which model generates it. This is a fixed project convention; implementers must not substitute their own model name.
- Any per-aspect check added to `PlayerDamageListener` MUST check `aspect.id().equals("blood")` as the literal first statement, before reading any config flag via `getBoolean`/`getInt`/`getDouble` — this is mandatory project-wide after a real production bug (a config-default leaking a behavior to every aspect lacking the key) was found in an earlier batch.
- Pure, branch-containing decision logic (no Bukkit API calls) gets direct unit tests with fakes, exactly like `AbilityCooldownManager`/`TemporaryEffectManager` and `PlayerDamageListener`'s existing static helpers. Logic that calls real Bukkit APIs (`Player`, `World`, `Block`, events) is manual-verification-only and is NOT mocked — this project has never mocked Bukkit types, matching `EarthActiveAbility`/`ThunderActiveAbility`/`VoidActiveAbility`'s precedent of leaving a single inlined precondition comparison untested when it has no branching worth isolating.
- Build/test command, used identically in every task:
  ```
  export JAVA_HOME="/home/erikkalstrom/.local/share/flatpak/runtime/org.freedesktop.Sdk.Extension.openjdk21/x86_64/26.08/d4d21c831d4b2abcc5c2c1f32d7ac4a5cac886db0328a94a259021e26e93689a/files/jvm/openjdk-21"
  export PATH="$JAVA_HOME/bin:/tmp/apache-maven-3.9.9/bin:$PATH"
  ```
- Starting test count before this plan: 53 (10 `PlayerDamageListenerTest`, 5 `VoidActiveAbilityTest`, plus 38 others). Each task below states the expected running total.
- No WorldGuard integration, no GUI, no crafting recipe — those remain Phase 3/4/5, out of scope here, matching every prior batch's phasing.

---

### Task 1: `TemporaryEffectManager`

**Files:**
- Create: `src/main/java/com/aspects/smp/ability/TemporaryEffectManager.java`
- Test: `src/test/java/com/aspects/smp/ability/TemporaryEffectManagerTest.java`

**Interfaces:**
- Consumes: nothing new (pure, no Bukkit dependency, no repository — unlike `AbilityCooldownManager` this has no persistence, per the design doc's explicit "temporary means temporary" requirement).
- Produces: `TemporaryEffectManager` with `startBuff(UUID playerId, String buffId, long durationMillis)`, `isActive(UUID playerId, String buffId) -> boolean`, and `clear(UUID playerId)`. Later tasks (3, 5, 6) call these three methods by these exact names.

- [ ] **Step 1: Write the failing tests**

```java
package com.aspects.smp.ability;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporaryEffectManagerTest {

    @Test
    void isActive_falseBeforeAnyBuffStarted() {
        TemporaryEffectManager manager = new TemporaryEffectManager();

        assertFalse(manager.isActive(UUID.randomUUID(), "blood-pact"));
    }

    @Test
    void startBuff_thenIsActive_trueUntilExpiry() {
        AtomicLong clock = new AtomicLong(0L);
        TemporaryEffectManager manager = new TemporaryEffectManager(clock::get);
        UUID playerId = UUID.randomUUID();

        manager.startBuff(playerId, "blood-pact", 5_000L);
        assertTrue(manager.isActive(playerId, "blood-pact"));

        clock.set(4_999L);
        assertTrue(manager.isActive(playerId, "blood-pact"));

        clock.set(5_000L);
        assertFalse(manager.isActive(playerId, "blood-pact"));
    }

    @Test
    void startBuff_reactivating_extendsExpiryRatherThanStacking() {
        AtomicLong clock = new AtomicLong(0L);
        TemporaryEffectManager manager = new TemporaryEffectManager(clock::get);
        UUID playerId = UUID.randomUUID();

        manager.startBuff(playerId, "blood-pact", 5_000L);
        clock.set(3_000L);
        manager.startBuff(playerId, "blood-pact", 5_000L);

        clock.set(7_999L);
        assertTrue(manager.isActive(playerId, "blood-pact"));
        clock.set(8_000L);
        assertFalse(manager.isActive(playerId, "blood-pact"));
    }

    @Test
    void clear_removesActiveBuff() {
        TemporaryEffectManager manager = new TemporaryEffectManager();
        UUID playerId = UUID.randomUUID();
        manager.startBuff(playerId, "blood-pact", 10_000L);

        manager.clear(playerId);

        assertFalse(manager.isActive(playerId, "blood-pact"));
    }

    @Test
    void isActive_falseForDifferentBuffId() {
        TemporaryEffectManager manager = new TemporaryEffectManager();
        UUID playerId = UUID.randomUUID();
        manager.startBuff(playerId, "blood-pact", 10_000L);

        assertFalse(manager.isActive(playerId, "other-buff"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest=TemporaryEffectManagerTest`
Expected: FAIL — `TemporaryEffectManager` does not exist.

- [ ] **Step 3: Create `src/main/java/com/aspects/smp/ability/TemporaryEffectManager.java`**

```java
package com.aspects.smp.ability;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public final class TemporaryEffectManager {
    private final LongSupplier clock;
    private final Map<UUID, Map<String, Long>> expiryByPlayer = new ConcurrentHashMap<>();

    public TemporaryEffectManager() {
        this(System::currentTimeMillis);
    }

    public TemporaryEffectManager(LongSupplier clock) {
        this.clock = clock;
    }

    public void startBuff(UUID playerId, String buffId, long durationMillis) {
        long expiresAt = clock.getAsLong() + durationMillis;
        expiryByPlayer.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>()).put(buffId, expiresAt);
    }

    public boolean isActive(UUID playerId, String buffId) {
        Map<String, Long> map = expiryByPlayer.get(playerId);
        if (map == null) {
            return false;
        }
        Long expiry = map.get(buffId);
        return expiry != null && expiry > clock.getAsLong();
    }

    public void clear(UUID playerId) {
        expiryByPlayer.remove(playerId);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test -Dtest=TemporaryEffectManagerTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Run the full test suite to confirm no regressions**

Run: `mvn -q test`
Expected: 58 tests total (53 before this task, plus these 5 new ones).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/aspects/smp/ability/TemporaryEffectManager.java src/test/java/com/aspects/smp/ability/TemporaryEffectManagerTest.java
git commit -m "$(cat <<'EOF'
Add TemporaryEffectManager for Blood's timed buff tracking

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Wire `TemporaryEffectManager` through `AspectsPlugin` and `AspectManager`

**Files:**
- Modify: `src/main/java/com/aspects/smp/aspect/AspectManager.java`
- Modify: `src/main/java/com/aspects/smp/AspectsPlugin.java`

**Interfaces:**
- Consumes: `TemporaryEffectManager` (Task 1).
- Produces: `AspectsPlugin.getTemporaryEffectManager() -> TemporaryEffectManager`, used by Task 3 (`PlayerDeathListener`), Task 5 (`PlayerDamageListener`'s new handler), and Task 6 (`BloodActiveAbility`).

This task only wires plumbing; it changes no runtime behavior for any existing Aspect (every call added is a new `temporaryEffectManager.clear(...)` line that's a no-op for players whose buff map is empty).

Note: `PlayerQuitListener` is NOT modified by this task. It already calls `plugin.getAspectManager().unload(event.getPlayer())`, and since `unload()` itself gains the new `clear()` call below, quitting a player already clears their buff state with no listener change needed.

- [ ] **Step 1: Modify `AspectManager`'s constructor and the three methods that clear per-player state**

In `src/main/java/com/aspects/smp/aspect/AspectManager.java`:

Add this import alongside the existing ones:
```java
import com.aspects.smp.ability.TemporaryEffectManager;
```

Change the constructor and field declarations from:
```java
    private final AspectRegistry registry;
    private final AspectAssignmentService assignmentService;
    private final PlayerDataRepository repository;
    private final AbilityCooldownManager cooldownManager;
    private final Logger logger;
    private final boolean excludeCurrentOnReroll;
    private final Executor mainThreadExecutor;

    private final Map<UUID, Aspect> assigned = new ConcurrentHashMap<>();

    public AspectManager(AspectRegistry registry, AspectAssignmentService assignmentService,
                          PlayerDataRepository repository, AbilityCooldownManager cooldownManager,
                          Logger logger, boolean excludeCurrentOnReroll, Executor mainThreadExecutor) {
        this.registry = registry;
        this.assignmentService = assignmentService;
        this.repository = repository;
        this.cooldownManager = cooldownManager;
        this.logger = logger;
        this.excludeCurrentOnReroll = excludeCurrentOnReroll;
        this.mainThreadExecutor = mainThreadExecutor;
    }
```
to:
```java
    private final AspectRegistry registry;
    private final AspectAssignmentService assignmentService;
    private final PlayerDataRepository repository;
    private final AbilityCooldownManager cooldownManager;
    private final TemporaryEffectManager temporaryEffectManager;
    private final Logger logger;
    private final boolean excludeCurrentOnReroll;
    private final Executor mainThreadExecutor;

    private final Map<UUID, Aspect> assigned = new ConcurrentHashMap<>();

    public AspectManager(AspectRegistry registry, AspectAssignmentService assignmentService,
                          PlayerDataRepository repository, AbilityCooldownManager cooldownManager,
                          TemporaryEffectManager temporaryEffectManager, Logger logger,
                          boolean excludeCurrentOnReroll, Executor mainThreadExecutor) {
        this.registry = registry;
        this.assignmentService = assignmentService;
        this.repository = repository;
        this.cooldownManager = cooldownManager;
        this.temporaryEffectManager = temporaryEffectManager;
        this.logger = logger;
        this.excludeCurrentOnReroll = excludeCurrentOnReroll;
        this.mainThreadExecutor = mainThreadExecutor;
    }
```

Change `unload` from:
```java
    public void unload(Player player) {
        Aspect aspect = assigned.remove(player.getUniqueId());
        if (aspect != null) {
            aspect.passive().onUnequip(player);
        }
        cooldownManager.clearCache(player.getUniqueId());
    }
```
to:
```java
    public void unload(Player player) {
        Aspect aspect = assigned.remove(player.getUniqueId());
        if (aspect != null) {
            aspect.passive().onUnequip(player);
        }
        cooldownManager.clearCache(player.getUniqueId());
        temporaryEffectManager.clear(player.getUniqueId());
    }
```

In `adminSet`, change:
```java
        assigned.put(player.getUniqueId(), aspect);
        repository.savePlayer(new PlayerAspectData(player.getUniqueId(), aspect.id(), System.currentTimeMillis()));
        cooldownManager.clearCache(player.getUniqueId());
        aspect.passive().onEquip(player, aspect.definition());
        return Optional.of(aspect);
    }

    public Optional<Aspect> adminReroll(Player player) {
```
to:
```java
        assigned.put(player.getUniqueId(), aspect);
        repository.savePlayer(new PlayerAspectData(player.getUniqueId(), aspect.id(), System.currentTimeMillis()));
        cooldownManager.clearCache(player.getUniqueId());
        temporaryEffectManager.clear(player.getUniqueId());
        aspect.passive().onEquip(player, aspect.definition());
        return Optional.of(aspect);
    }

    public Optional<Aspect> adminReroll(Player player) {
```
(This matches the single `cooldownManager.clearCache(...)` call inside `adminSet` — add the new line directly after it.)

In `adminReroll`, change:
```java
        Aspect aspect = picked.get();
        assigned.put(player.getUniqueId(), aspect);
        repository.savePlayer(new PlayerAspectData(player.getUniqueId(), aspect.id(), System.currentTimeMillis()));
        cooldownManager.clearCache(player.getUniqueId());
        aspect.passive().onEquip(player, aspect.definition());
        return Optional.of(aspect);
    }
}
```
to:
```java
        Aspect aspect = picked.get();
        assigned.put(player.getUniqueId(), aspect);
        repository.savePlayer(new PlayerAspectData(player.getUniqueId(), aspect.id(), System.currentTimeMillis()));
        cooldownManager.clearCache(player.getUniqueId());
        temporaryEffectManager.clear(player.getUniqueId());
        aspect.passive().onEquip(player, aspect.definition());
        return Optional.of(aspect);
    }
}
```

- [ ] **Step 2: Add the field, getter, and construction to `AspectsPlugin`**

In `src/main/java/com/aspects/smp/AspectsPlugin.java`, add this import alongside the existing `com.aspects.smp.ability.*` imports:
```java
import com.aspects.smp.ability.TemporaryEffectManager;
```

Add a field next to the existing `private AbilityCooldownManager cooldownManager;`:
```java
    private AbilityCooldownManager cooldownManager;
    private TemporaryEffectManager temporaryEffectManager;
```

In `onEnable()`, change:
```java
        cooldownManager = new AbilityCooldownManager(dataRepository);
        aspectManager = new AspectManager(aspectRegistry, new AspectAssignmentService(), dataRepository,
                cooldownManager, getLogger(), configManager.general().excludeCurrentOnReroll(),
                runnable -> getServer().getScheduler().runTask(this, runnable));
```
to:
```java
        cooldownManager = new AbilityCooldownManager(dataRepository);
        temporaryEffectManager = new TemporaryEffectManager();
        aspectManager = new AspectManager(aspectRegistry, new AspectAssignmentService(), dataRepository,
                cooldownManager, temporaryEffectManager, getLogger(), configManager.general().excludeCurrentOnReroll(),
                runnable -> getServer().getScheduler().runTask(this, runnable));
```

Add a getter next to the existing `getAspectManager()`:
```java
    public AspectManager getAspectManager() {
        return aspectManager;
    }

    public TemporaryEffectManager getTemporaryEffectManager() {
        return temporaryEffectManager;
    }
```

- [ ] **Step 3: Build and test**

Run: `mvn -q test`
Expected: BUILD SUCCESS, 58 tests passing (no new tests in this task — it's pure plumbing with no new branching logic; `AspectManager` has no existing dedicated unit test suite to extend, consistent with it already not having one before this task).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/aspects/smp/aspect/AspectManager.java src/main/java/com/aspects/smp/AspectsPlugin.java
git commit -m "$(cat <<'EOF'
Wire TemporaryEffectManager through AspectManager and AspectsPlugin

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: `PlayerDeathListener`

**Files:**
- Create: `src/main/java/com/aspects/smp/listener/PlayerDeathListener.java`
- Modify: `src/main/java/com/aspects/smp/AspectsPlugin.java`

**Interfaces:**
- Consumes: `AspectsPlugin.getTemporaryEffectManager()` (Task 2).
- Produces: nothing new for later tasks — this is a terminal wiring task, like `PlayerQuitListener`.

- [ ] **Step 1: Create `src/main/java/com/aspects/smp/listener/PlayerDeathListener.java`**

```java
package com.aspects.smp.listener;

import com.aspects.smp.AspectsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class PlayerDeathListener implements Listener {
    private final AspectsPlugin plugin;

    public PlayerDeathListener(AspectsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.getTemporaryEffectManager().clear(event.getEntity().getUniqueId());
    }
}
```

No test file: this is a thin Bukkit event listener with no branching logic, matching `PlayerQuitListener`'s existing precedent (also untested) — manual-verification-only.

- [ ] **Step 2: Register the listener in `AspectsPlugin.onEnable()`**

Add this import alongside the existing `com.aspects.smp.listener.*` imports:
```java
import com.aspects.smp.listener.PlayerDeathListener;
```

Change:
```java
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new ActivationListener(abilityManager, this), this);
        getServer().getPluginManager().registerEvents(new PlayerDamageListener(this), this);
```
to:
```java
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new ActivationListener(abilityManager, this), this);
        getServer().getPluginManager().registerEvents(new PlayerDamageListener(this), this);
```

- [ ] **Step 3: Build and test**

Run: `mvn -q package`
Expected: BUILD SUCCESS, 58 tests passing, jar built.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/aspects/smp/listener/PlayerDeathListener.java src/main/java/com/aspects/smp/AspectsPlugin.java
git commit -m "$(cat <<'EOF'
Add PlayerDeathListener to clear temporary buffs on death

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: `BloodPassiveAbility` (no-op)

**Files:**
- Create: `src/main/java/com/aspects/smp/ability/impl/BloodPassiveAbility.java`

**Interfaces:**
- Consumes: `PassiveAbility` (Phase 1 Task 4).
- Produces: `BloodPassiveAbility`, registered in Task 8's factory tables.

- [ ] **Step 1: Create `src/main/java/com/aspects/smp/ability/impl/BloodPassiveAbility.java`**

```java
package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.PassiveAbility;
import com.aspects.smp.aspect.AspectDefinition;
import org.bukkit.entity.Player;

public final class BloodPassiveAbility implements PassiveAbility {
    @Override
    public void onEquip(Player player, AspectDefinition definition) {
        // The melee-damage bonus has no vanilla potion-effect equivalent. It is enforced
        // live by PlayerDamageListener checking the damaging player's current Aspect on
        // each EntityDamageByEntityEvent, so there is no state to apply here.
    }

    @Override
    public void onUnequip(Player player) {
        // No state to remove — see onEquip.
    }
}
```

No test: a no-op with no branching, matching `FrostPassiveAbility`/`VoidPassiveAbility`'s precedent exactly.

- [ ] **Step 2: Build**

Run: `mvn -q package -DskipTests`
Expected: exit 0 (BUILD SUCCESS; quiet mode prints no success banner, so the exit code is the evidence).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/aspects/smp/ability/impl/BloodPassiveAbility.java
git commit -m "$(cat <<'EOF'
Add BloodPassiveAbility (enforcement lives in PlayerDamageListener)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: `PlayerDamageListener`'s melee-damage-bonus handler

**Files:**
- Modify: `src/main/java/com/aspects/smp/listener/PlayerDamageListener.java`
- Modify: `src/test/java/com/aspects/smp/listener/PlayerDamageListenerTest.java`

**Interfaces:**
- Consumes: `AspectsPlugin.getTemporaryEffectManager()` (Task 2); the buff id string `"blood-pact"`, which Task 6's `BloodActiveAbility` must start under the exact same literal.
- Produces: a new package-visible static method `meleeDamageMultiplier(Aspect aspect, boolean buffActive) -> double`, unit-tested directly (same pattern as `fallDamageMultiplier`/`cancelsFreezeDamage`).

- [ ] **Step 1: Write the failing tests**

Append these tests to the end of the `PlayerDamageListenerTest` class (inside the closing brace, after the existing `fallDamageMultiplier_returnsOne_forFrostEvenIfUnrelatedFlagEnabled` test):

```java

    @Test
    void meleeDamageMultiplier_appliesPassiveBonus_forBloodWithNoBuffActive() {
        Aspect blood = aspect("blood", Map.of());

        assertEquals(1.1, PlayerDamageListener.meleeDamageMultiplier(blood, false), 1e-9);
    }

    @Test
    void meleeDamageMultiplier_appliesPassiveAndActiveBonus_whenBuffActive() {
        Aspect blood = aspect("blood", Map.of());

        assertEquals(1.3, PlayerDamageListener.meleeDamageMultiplier(blood, true), 1e-9);
    }

    @Test
    void meleeDamageMultiplier_returnsOne_whenPassiveFlagDisabledAndBuffInactive() {
        Aspect blood = aspect("blood", Map.of("passive.melee-damage-bonus", false));

        assertEquals(1.0, PlayerDamageListener.meleeDamageMultiplier(blood, false), 1e-9);
    }

    @Test
    void meleeDamageMultiplier_respectsCustomPercentages() {
        Aspect blood = aspect("blood",
                Map.of("passive.melee-damage-bonus-percent", 15, "active.bonus-percent", 25));

        assertEquals(1.40, PlayerDamageListener.meleeDamageMultiplier(blood, true), 1e-9);
    }

    @Test
    void meleeDamageMultiplier_returnsOne_forFireEvenWithBuffActiveTrue() {
        Aspect fire = aspect("fire", Map.of());

        assertEquals(1.0, PlayerDamageListener.meleeDamageMultiplier(fire, true), 1e-9);
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest=PlayerDamageListenerTest`
Expected: FAIL — `meleeDamageMultiplier` does not exist.

- [ ] **Step 3: Add the new handler and static method to `PlayerDamageListener`**

Replace the full contents of `src/main/java/com/aspects/smp/listener/PlayerDamageListener.java` with:

```java
package com.aspects.smp.listener;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.Aspect;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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

    @EventHandler
    public void onMeleeDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) {
            return;
        }
        Aspect aspect = plugin.getAspectManager().getAspect(damager.getUniqueId()).orElse(null);
        if (aspect == null) {
            return;
        }
        boolean buffActive = plugin.getTemporaryEffectManager().isActive(damager.getUniqueId(), "blood-pact");
        double multiplier = meleeDamageMultiplier(aspect, buffActive);
        if (multiplier != 1.0) {
            event.setDamage(event.getDamage() * multiplier);
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
        reductionPercent = Math.max(0, Math.min(100, reductionPercent));
        return 1.0 - (reductionPercent / 100.0);
    }

    static double meleeDamageMultiplier(Aspect aspect, boolean buffActive) {
        if (!aspect.id().equals("blood")) {
            return 1.0;
        }
        double multiplier = 1.0;
        if (aspect.definition().getBoolean("passive.melee-damage-bonus", true)) {
            int bonusPercent = aspect.definition().getInt("passive.melee-damage-bonus-percent", 10);
            multiplier += bonusPercent / 100.0;
        }
        if (buffActive) {
            int activeBonusPercent = aspect.definition().getInt("active.bonus-percent", 20);
            multiplier += activeBonusPercent / 100.0;
        }
        return multiplier;
    }
}
```

(`onDamage` and its two static helpers `cancelsFreezeDamage`/`fallDamageMultiplier` are unchanged from before this task — only the new `onMeleeDamage` handler and `meleeDamageMultiplier` method, plus the one new import, are added.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test -Dtest=PlayerDamageListenerTest`
Expected: PASS (15 tests: 10 existing + 5 new).

- [ ] **Step 5: Run the full test suite to confirm no regressions**

Run: `mvn -q test`
Expected: 63 tests total (58 before this task, plus these 5 new ones).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/aspects/smp/listener/PlayerDamageListener.java src/test/java/com/aspects/smp/listener/PlayerDamageListenerTest.java
git commit -m "$(cat <<'EOF'
Add Blood's melee-damage-bonus handler to PlayerDamageListener

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: `BloodActiveAbility`

**Files:**
- Create: `src/main/java/com/aspects/smp/ability/impl/BloodActiveAbility.java`

**Interfaces:**
- Consumes: `Ability`/`AbilityContext` (Phase 1 Task 8); `SoundUtil.play`/`ParticleUtil.spawn` (Phase 1 Task 10); `AspectDefinition.getDouble`/`getInt` (Phase 1 Task 4); `AspectsPlugin.getTemporaryEffectManager()` (Task 2); `TemporaryEffectManager.startBuff` (Task 1).
- Produces: `BloodActiveAbility`. Must call `startBuff` with the exact buff id literal `"blood-pact"` — the same literal Task 5's `onMeleeDamage` checks via `isActive`.

The health-affordability precondition (`player.getHealth() - sacrificeAmount > 0`) is a single comparison with no branching complexity worth isolating — per the design doc, it's inlined directly in `activate()` rather than extracted into a tested function, the same way `EarthActiveAbility`/`ThunderActiveAbility`/`FireActiveAbility` have no dedicated test file for their Bukkit-API-dependent `activate()` bodies.

- [ ] **Step 1: Create `src/main/java/com/aspects/smp/ability/impl/BloodActiveAbility.java`**

```java
package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.AbilityContext;
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.util.ParticleUtil;
import com.aspects.smp.util.SoundUtil;
import org.bukkit.entity.Player;

public final class BloodActiveAbility implements Ability {
    @Override
    public boolean activate(AbilityContext context) {
        Player player = context.player();
        AspectDefinition definition = context.aspect().definition();

        double sacrificeAmount = definition.getDouble("active.sacrifice-amount", 2.0);
        if (player.getHealth() - sacrificeAmount <= 0) {
            return false;
        }

        int buffDurationTicks = definition.getInt("active.buff-duration-ticks", 100);
        long buffDurationMillis = buffDurationTicks * 50L;
        context.plugin().getTemporaryEffectManager()
                .startBuff(player.getUniqueId(), "blood-pact", buffDurationMillis);

        player.damage(sacrificeAmount);

        SoundUtil.play(player, "entity.player.hurt", 1.0f, 0.6f, context.plugin().getLogger());
        ParticleUtil.spawn(player.getLocation(), "DAMAGE_INDICATOR", 15, context.plugin().getLogger());
        return true;
    }
}
```

No test file, per the rationale above.

- [ ] **Step 2: Build**

Run: `mvn -q package -DskipTests`
Expected: exit 0.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/aspects/smp/ability/impl/BloodActiveAbility.java
git commit -m "$(cat <<'EOF'
Implement Blood's active ability (health-sacrifice damage buff)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Enable Blood in `aspects.yml`

**Files:**
- Modify: `src/main/resources/aspects.yml`

**Interfaces:**
- Consumes: nothing.
- Produces: the config keys Tasks 5 and 6 read: `passive.melee-damage-bonus`, `passive.melee-damage-bonus-percent`, `active.cooldown-seconds`, `active.sacrifice-amount`, `active.bonus-percent`, `active.buff-duration-ticks`.

- [ ] **Step 1: Update the header comment**

Change:
```yaml
# fire, tide, frost, wind, earth, thunder, and void are implemented (Phase 1,
# Phase 2 Batches A and B1, and Batch B2a). blood is listed here with its
# final rarity/description so the file's shape is correct, but its abilities
# are NOT implemented yet — it ships disabled until Batch B2b adds its code.
```
to:
```yaml
# All eight Aspects — fire, tide, frost, wind, earth, thunder, void, and
# blood — are implemented as of Phase 2 Batch B2b. Phase 2 is complete.
```

- [ ] **Step 2: Replace the `blood` block**

Change:
```yaml
  blood:
    enabled: false
    display-name: "<dark_red>Blood</dark_red>"
    description: "Not yet implemented (planned for Phase 2)."
    rarity: EPIC
    weight: 0
```
to:
```yaml
  blood:
    enabled: true
    display-name: "<dark_red>Blood</dark_red>"
    description: "Sacrifice, aggression, and overwhelming melee power."
    rarity: EPIC
    weight: 10
    passive:
      melee-damage-bonus: true
      melee-damage-bonus-percent: 10
    active:
      cooldown-seconds: 28
      sacrifice-amount: 2.0
      bonus-percent: 20
      buff-duration-ticks: 100
```

- [ ] **Step 3: Build**

Run: `mvn -q package -DskipTests`
Expected: exit 0 (config is only validated against enabled Aspects actually having registered factories, which Task 8 provides — until then, this task alone leaves `blood` enabled with no factory, which `AspectsPlugin.buildRegistry` already handles gracefully by logging "not yet implemented in code; skipping registration" and continuing, exactly as it did for `void` before Batch B2a's Task 5).

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/aspects.yml
git commit -m "$(cat <<'EOF'
Enable Blood in aspects.yml with real settings

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: Wire Blood into the factory tables, full build

**Files:**
- Modify: `src/main/java/com/aspects/smp/AspectsPlugin.java`

**Interfaces:**
- Consumes: everything from Tasks 1-7, plus the existing factory-table structure (Batch B1 Task 2).
- Produces: nothing new for later tasks — this is the final integration point for Batch B2b, and for Phase 2 as a whole.

- [ ] **Step 1: Read the current `src/main/java/com/aspects/smp/AspectsPlugin.java`**

(It should have 7 entries each in `PASSIVE_FACTORIES`/`ACTIVE_FACTORIES` — fire, tide, frost, wind, earth, thunder, void — plus the `TemporaryEffectManager` field/getter from Task 2 and the `PlayerDeathListener` registration from Task 3. If it looks different from what this plan expects, stop and report BLOCKED rather than guessing.)

- [ ] **Step 2: Add two imports**

Add these two import lines alongside the existing `com.aspects.smp.ability.impl.*` imports, in alphabetical position (`Blood*` sorts before `Earth*`, since "B" < "E"):

```java
import com.aspects.smp.ability.impl.BloodActiveAbility;
import com.aspects.smp.ability.impl.BloodPassiveAbility;
```

- [ ] **Step 3: Extend both factory-table fields**

Find the two `Map.of(...)` fields (`PASSIVE_FACTORIES`, `ACTIVE_FACTORIES`). Add one more entry to each, right after the `"void"` entry:

```java
            "void", VoidPassiveAbility::new,
            "blood", BloodPassiveAbility::new
```

and

```java
            "void", VoidActiveAbility::new,
            "blood", BloodActiveAbility::new
```

(Each map now has 8 entries total: fire, tide, frost, wind, earth, thunder, void, blood.)

Do not change anything else in the file — `buildRegistry`'s loop body, `onEnable`, `onDisable`, and the getters all stay exactly as they are; the loop is already generic over whatever is in the maps.

- [ ] **Step 4: Full build and test**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`, all 63 unit tests pass, and `target/aspects-1.0.0-SNAPSHOT.jar` exists.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/aspects/smp/AspectsPlugin.java
git commit -m "$(cat <<'EOF'
Wire Blood into the factory tables

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 6: Note for the controller, not this task's implementer**

Live verification against a running Paper 1.21.11 server is the controller's responsibility after this task's review passes, the same way every prior batch's final wiring task was handled — do not attempt this as the task implementer; there is no running server in that environment. Specifically verify: a Blood player's melee damage is ~10% higher than an unaffected baseline; activating Blood Pact at low health (below the sacrifice amount) fails cleanly with no health lost and no cooldown started; activating it at sufficient health costs exactly `sacrifice-amount` health and raises the melee bonus to ~30% for `buff-duration-ticks` (5s), after which it drops back to ~10%; dying while Blood Pact is active does not leave the buff active after respawn; `/aspects admin set <player> blood` works via the existing command with no changes needed there.
