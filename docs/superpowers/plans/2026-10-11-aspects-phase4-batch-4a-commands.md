# Aspects Plugin — Phase 4 Batch 4a (Player & Admin Commands) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement every remaining player and admin text command from the original spec (`/aspects list`,
`/aspects inspect`, `/aspects help`, `/aspects admin give-reroller|reset|clear-cooldown|reload|list|debug`),
plus the full permission node set and tab completion for all of it. No new items, no new GUIs — this batch
is pure command-layer work reusing managers that already exist.

**Architecture:** `AspectManager` gains a `resetAspect(Player)` method (a third variant alongside
`adminReroll`/`rerollViaItem`, sharing the existing `performReroll` helper for its "immediate reassign"
branch) and a new constructor parameter for the reset-behavior config flag. `PlayerDataRepository` gains a
`deletePlayer(UUID)` method for reset's "wait for next join" branch. `AdminCommand` and `AspectsCommand` are
extended with one new private method per subcommand, following the exact structural pattern every existing
subcommand already uses (permission check, argument validation, call a manager method, report the result).

**Tech Stack:** Java 21, Paper API `1.21.11-R0.1-SNAPSHOT`, Maven, JUnit 5.

## Global Constraints

- Every commit ends with exactly: `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>` — regardless of which model generates it. This is a fixed project convention; implementers must not substitute their own model name.
- Every new admin subcommand re-checks its own specific permission node independently — never rely on the
  umbrella `aspects.admin` permission alone for a specific destructive action, matching this project's
  existing `handleSet`/`handleReroll` pattern and the original spec's explicit "all administrative
  operations must verify permissions independently."
- Pure, branch-containing decision logic (no Bukkit API calls) gets direct unit tests, exactly like
  `AspectsConfigParser`'s existing tests. Command handler methods that touch `CommandSender`/`Player`/
  `Bukkit` are manual-verification-only, matching every existing command handler in this project (none of
  `AdminCommand`'s or `AspectsCommand`'s existing methods have unit tests today).
- Build/test command, used identically in every task:
  ```
  export JAVA_HOME="/home/erikkalstrom/.local/share/flatpak/runtime/org.freedesktop.Sdk.Extension.openjdk21/x86_64/26.08/d4d21c831d4b2abcc5c2c1f32d7ac4a5cac886db0328a94a259021e26e93689a/files/jvm/openjdk-21"
  export PATH="$JAVA_HOME/bin:/tmp/apache-maven-3.9.9/bin:$PATH"
  ```
- Starting test count before this plan: 77. Each task below states the expected running total.
- The Aspect Selector item (Batch 4b) and the Admin GUI (Batch 4c) are explicitly out of scope here.

---

### Task 1: `GeneralConfig.resetImmediateReassign`

**Files:**
- Modify: `src/main/java/com/aspects/smp/config/GeneralConfig.java`
- Modify: `src/main/java/com/aspects/smp/config/AspectsConfigParser.java`
- Modify: `src/main/resources/config.yml`
- Test: `src/test/java/com/aspects/smp/config/AspectsConfigParserTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `GeneralConfig.resetImmediateReassign() -> boolean`, used by Task 3's `AspectsPlugin` wiring and
  Task 4's `/aspects admin debug` output.

- [ ] **Step 1: Add the field to `GeneralConfig`**

Change:
```java
public record GeneralConfig(
        boolean debug,
        String sqliteFile,
        boolean firstJoinEnabled,
        String firstJoinSound,
        String firstJoinParticle,
        int firstJoinParticleCount,
        List<String> blockedWorlds,
        boolean excludeCurrentOnReroll,
        boolean revealAnimationEnabled
) {
}
```
to:
```java
public record GeneralConfig(
        boolean debug,
        String sqliteFile,
        boolean firstJoinEnabled,
        String firstJoinSound,
        String firstJoinParticle,
        int firstJoinParticleCount,
        List<String> blockedWorlds,
        boolean excludeCurrentOnReroll,
        boolean revealAnimationEnabled,
        boolean resetImmediateReassign
) {
}
```

- [ ] **Step 2: Write the failing test**

Append this test to `AspectsConfigParserTest` (after the last existing test):

```java

    @Test
    void parseGeneral_defaultsResetImmediateReassignToTrue() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(""));
        List<String> errors = new ArrayList<>();

        GeneralConfig config = AspectsConfigParser.parseGeneral(yml, errors);

        assertTrue(config.resetImmediateReassign());
    }

    @Test
    void parseGeneral_readsResetImmediateReassignFalse() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "reset:\n  immediate-reassign: false\n"));
        List<String> errors = new ArrayList<>();

        GeneralConfig config = AspectsConfigParser.parseGeneral(yml, errors);

        assertFalse(config.resetImmediateReassign());
    }
```
(`assertFalse` is already statically imported in this test class from an earlier test; if it isn't, add
`import static org.junit.jupiter.api.Assertions.assertFalse;` alongside the existing `assertTrue`/`assertEquals` imports.)

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -q test -Dtest=AspectsConfigParserTest`
Expected: FAIL — compile error, `GeneralConfig` constructor doesn't match (missing `resetImmediateReassign` argument).

- [ ] **Step 4: Update `AspectsConfigParser.parseGeneral`**

Change:
```java
        boolean excludeCurrentOnReroll = yml.getBoolean("reroll.exclude-current-aspect", true);
        boolean revealAnimationEnabled = yml.getBoolean("reroll.reveal-animation-enabled", true);

        if (sqliteFile == null || sqliteFile.isBlank()) {
            errors.add("storage.sqlite-file must not be blank");
            sqliteFile = "playerdata.db";
        }
        if (firstJoinParticleCount < 0) {
            errors.add("first-join.particle-count must not be negative");
        }

        return new GeneralConfig(debug, sqliteFile, firstJoinEnabled, firstJoinSound,
                firstJoinParticle, firstJoinParticleCount, blockedWorlds, excludeCurrentOnReroll,
                revealAnimationEnabled);
```
to:
```java
        boolean excludeCurrentOnReroll = yml.getBoolean("reroll.exclude-current-aspect", true);
        boolean revealAnimationEnabled = yml.getBoolean("reroll.reveal-animation-enabled", true);
        boolean resetImmediateReassign = yml.getBoolean("reset.immediate-reassign", true);

        if (sqliteFile == null || sqliteFile.isBlank()) {
            errors.add("storage.sqlite-file must not be blank");
            sqliteFile = "playerdata.db";
        }
        if (firstJoinParticleCount < 0) {
            errors.add("first-join.particle-count must not be negative");
        }

        return new GeneralConfig(debug, sqliteFile, firstJoinEnabled, firstJoinSound,
                firstJoinParticle, firstJoinParticleCount, blockedWorlds, excludeCurrentOnReroll,
                revealAnimationEnabled, resetImmediateReassign);
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -q test -Dtest=AspectsConfigParserTest`
Expected: PASS (all tests in this class, including the 2 new ones).

- [ ] **Step 6: Document the new key in `config.yml`**

Add this section to `src/main/resources/config.yml`, after the existing `reroll:` section:
```yaml
reset:
  # When true, /aspects admin reset <player> immediately assigns a new random Aspect (it may be the
  # same Aspect again — unlike a reroll, a reset does not exclude the player's current Aspect).
  # When false, the player's Aspect data is cleared entirely and they receive a new random Aspect
  # the next time they join, the same way a brand-new player would.
  immediate-reassign: true
```

- [ ] **Step 7: Run the full test suite to confirm no regressions**

Run: `mvn -q test`
Expected: 79 tests total (77 before this task, plus these 2 new ones).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/aspects/smp/config/GeneralConfig.java src/main/java/com/aspects/smp/config/AspectsConfigParser.java src/main/resources/config.yml src/test/java/com/aspects/smp/config/AspectsConfigParserTest.java
git commit -m "$(cat <<'EOF'
Add reset.immediate-reassign config option

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: `PlayerDataRepository.deletePlayer`

**Files:**
- Modify: `src/main/java/com/aspects/smp/data/PlayerDataRepository.java`
- Modify: `src/main/java/com/aspects/smp/data/SQLitePlayerDataRepository.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `PlayerDataRepository.deletePlayer(UUID playerId) -> CompletableFuture<Void>`, used by Task 3's
  `AspectManager.resetAspect`.
- Test: `src/test/java/com/aspects/smp/data/SQLitePlayerDataRepositoryTest.java` (this file already exists,
  with a `newRepository()` helper that spins up a temp SQLite file — add one test to it, following its exact
  existing style).

- [ ] **Step 1: Write the failing test**

Append this test to `SQLitePlayerDataRepositoryTest` (after the last existing test, inside the closing brace):

```java

    @Test
    void deletePlayer_removesStoredData() throws Exception {
        SQLitePlayerDataRepository repo = newRepository();
        UUID playerId = UUID.randomUUID();
        repo.savePlayer(new PlayerAspectData(playerId, "fire", 1L)).get();

        repo.deletePlayer(playerId).get();
        Optional<PlayerAspectData> loaded = repo.loadPlayer(playerId).get();

        assertTrue(loaded.isEmpty());
        repo.close();
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=SQLitePlayerDataRepositoryTest`
Expected: FAIL — compile error, `deletePlayer` does not exist on `SQLitePlayerDataRepository`.

- [ ] **Step 3: Add the method to the `PlayerDataRepository` interface**

Change:
```java
public interface PlayerDataRepository {
    CompletableFuture<Optional<PlayerAspectData>> loadPlayer(UUID playerId);

    CompletableFuture<Void> savePlayer(PlayerAspectData data);

    CompletableFuture<Map<String, Long>> loadCooldowns(UUID playerId);

    CompletableFuture<Void> saveCooldown(UUID playerId, String abilityId, long expiresAtMillis);

    void close();
}
```
to:
```java
public interface PlayerDataRepository {
    CompletableFuture<Optional<PlayerAspectData>> loadPlayer(UUID playerId);

    CompletableFuture<Void> savePlayer(PlayerAspectData data);

    CompletableFuture<Void> deletePlayer(UUID playerId);

    CompletableFuture<Map<String, Long>> loadCooldowns(UUID playerId);

    CompletableFuture<Void> saveCooldown(UUID playerId, String abilityId, long expiresAtMillis);

    void close();
}
```

- [ ] **Step 4: Implement it in `SQLitePlayerDataRepository`**

Add this method right after the existing `savePlayer` method:
```java
    @Override
    public CompletableFuture<Void> deletePlayer(UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM players WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, playerId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.severe("Failed to delete player data for " + playerId + ": " + e.getMessage());
            }
        }, executor);
    }
```

- [ ] **Step 5: Build and test**

Run: `mvn -q test`
Expected: BUILD SUCCESS, 80 tests total (79 before this task, plus this 1 new one).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/aspects/smp/data/PlayerDataRepository.java src/main/java/com/aspects/smp/data/SQLitePlayerDataRepository.java src/test/java/com/aspects/smp/data/SQLitePlayerDataRepositoryTest.java
git commit -m "$(cat <<'EOF'
Add PlayerDataRepository.deletePlayer for the deferred reset path

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: `AspectManager.resetAspect`

**Files:**
- Modify: `src/main/java/com/aspects/smp/aspect/AspectManager.java`
- Modify: `src/main/java/com/aspects/smp/AspectsPlugin.java`

**Interfaces:**
- Consumes: `GeneralConfig.resetImmediateReassign()` (Task 1); `PlayerDataRepository.deletePlayer` (Task 2).
- Produces: `AspectManager.resetAspect(Player player) -> Optional<Aspect>` — present when a new Aspect was
  immediately assigned, empty when the player's data was cleared for next-join reassignment instead. Used
  by Task 4's `/aspects admin reset`.

- [ ] **Step 1: Add the new constructor parameter**

In `src/main/java/com/aspects/smp/aspect/AspectManager.java`, change:
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
to:
```java
    private final AspectRegistry registry;
    private final AspectAssignmentService assignmentService;
    private final PlayerDataRepository repository;
    private final AbilityCooldownManager cooldownManager;
    private final TemporaryEffectManager temporaryEffectManager;
    private final Logger logger;
    private final boolean excludeCurrentOnReroll;
    private final boolean resetImmediateReassign;
    private final Executor mainThreadExecutor;

    private final Map<UUID, Aspect> assigned = new ConcurrentHashMap<>();

    public AspectManager(AspectRegistry registry, AspectAssignmentService assignmentService,
                          PlayerDataRepository repository, AbilityCooldownManager cooldownManager,
                          TemporaryEffectManager temporaryEffectManager, Logger logger,
                          boolean excludeCurrentOnReroll, boolean resetImmediateReassign,
                          Executor mainThreadExecutor) {
        this.registry = registry;
        this.assignmentService = assignmentService;
        this.repository = repository;
        this.cooldownManager = cooldownManager;
        this.temporaryEffectManager = temporaryEffectManager;
        this.logger = logger;
        this.excludeCurrentOnReroll = excludeCurrentOnReroll;
        this.resetImmediateReassign = resetImmediateReassign;
        this.mainThreadExecutor = mainThreadExecutor;
    }
```

- [ ] **Step 2: Add `resetAspect`**

Add this new public method right after the existing `rerollViaItem` method:
```java
    public Optional<Aspect> resetAspect(Player player) {
        if (resetImmediateReassign) {
            return performReroll(player, false);
        }
        Aspect previous = assigned.remove(player.getUniqueId());
        if (previous != null) {
            previous.passive().onUnequip(player);
        }
        repository.deletePlayer(player.getUniqueId());
        cooldownManager.clearCache(player.getUniqueId());
        temporaryEffectManager.clear(player.getUniqueId());
        return Optional.empty();
    }
```
(The `false` passed to `performReroll` means "do not exclude the current Aspect" — a reset is explicitly
allowed to land on the same Aspect again, per the design doc's distinction between "reset" and "reroll."
This is a deliberate, hardcoded `false` for the reset path specifically — it does NOT read
`excludeCurrentOnReroll`, which only applies to `adminReroll`/`rerollViaItem`.)

- [ ] **Step 3: Update the call site in `AspectsPlugin`**

In `src/main/java/com/aspects/smp/AspectsPlugin.java`, change:
```java
        aspectManager = new AspectManager(aspectRegistry, new AspectAssignmentService(), dataRepository,
                cooldownManager, temporaryEffectManager, getLogger(), configManager.general().excludeCurrentOnReroll(),
                runnable -> getServer().getScheduler().runTask(this, runnable));
```
to:
```java
        aspectManager = new AspectManager(aspectRegistry, new AspectAssignmentService(), dataRepository,
                cooldownManager, temporaryEffectManager, getLogger(), configManager.general().excludeCurrentOnReroll(),
                configManager.general().resetImmediateReassign(),
                runnable -> getServer().getScheduler().runTask(this, runnable));
```

- [ ] **Step 4: Build and test**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`, all 80 tests pass, jar built.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/aspects/smp/aspect/AspectManager.java src/main/java/com/aspects/smp/AspectsPlugin.java
git commit -m "$(cat <<'EOF'
Add AspectManager.resetAspect

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: `AdminCommand` — `give-reroller`, `reset`, `clear-cooldown`, `reload`, `list`, `debug`, plus permissions

**Files:**
- Modify: `src/main/java/com/aspects/smp/command/AdminCommand.java`
- Modify: `src/main/resources/plugin.yml`

**Interfaces:**
- Consumes: `AspectManager.resetAspect` (Task 3); `CustomItemManager.createReroller` (Phase 3 Batch 3a);
  `AbilityCooldownManager.clearCache`/`remainingMillis` (Phase 1); `ConfigManager.loadAll`/`recipes`/
  `general` (Phase 1/Batch 3a); `RerollerRecipeManager.register` (Phase 3 Batch 3a);
  `AspectRegistry.all()` (Phase 1).
- Produces: `AdminCommand.clampGiveAmount(int) -> int`, a package-visible pure static method, unit-tested
  directly. Everything else in this file remains manual-verification-only.

- [ ] **Step 1: Write the failing test for the pure amount-clamping logic**

Create `src/test/java/com/aspects/smp/command/AdminCommandTest.java`:
```java
package com.aspects.smp.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminCommandTest {

    @Test
    void clampGiveAmount_returnsRequestedAmount_whenInRange() {
        assertEquals(5, AdminCommand.clampGiveAmount(5));
    }

    @Test
    void clampGiveAmount_clampsZeroOrBelow_toOne() {
        assertEquals(1, AdminCommand.clampGiveAmount(0));
        assertEquals(1, AdminCommand.clampGiveAmount(-10));
    }

    @Test
    void clampGiveAmount_clampsAboveSixtyFour_toSixtyFour() {
        assertEquals(64, AdminCommand.clampGiveAmount(1000));
    }

    @Test
    void clampGiveAmount_allowsExactlySixtyFour() {
        assertEquals(64, AdminCommand.clampGiveAmount(64));
    }

    @Test
    void clampGiveAmount_allowsExactlyOne() {
        assertEquals(1, AdminCommand.clampGiveAmount(1));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=AdminCommandTest`
Expected: FAIL — `AdminCommand.clampGiveAmount` does not exist.

- [ ] **Step 3: Replace the full contents of `AdminCommand.java`**

```java
package com.aspects.smp.command;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.Aspect;
import com.aspects.smp.config.ConfigValidationException;
import com.aspects.smp.item.CustomItemManager;
import com.aspects.smp.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;

public final class AdminCommand {
    private final AspectsPlugin plugin;

    public AdminCommand(AspectsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>Usage: /aspects admin <set|reroll|reset|give-reroller|clear-cooldown|reload|list|debug> ...</red>"));
            return true;
        }
        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "set" -> handleSet(sender, args);
            case "reroll" -> handleReroll(sender, args);
            case "reset" -> handleReset(sender, args);
            case "give-reroller" -> handleGiveReroller(sender, args);
            case "clear-cooldown" -> handleClearCooldown(sender, args);
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender);
            case "debug" -> handleDebug(sender, args);
            default -> {
                sender.sendMessage(MessageUtil.parse("<red>Unknown admin subcommand '" + sub + "'.</red>"));
                yield true;
            }
        };
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aspects.admin.set")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse("<red>Usage: /aspects admin set <player> <aspect></red>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse("<red>Player '" + args[1] + "' is not online.</red>"));
            return true;
        }
        String aspectId = args[2].toLowerCase();
        Optional<Aspect> result = plugin.getAspectManager().adminSet(target, aspectId);
        if (result.isEmpty()) {
            sender.sendMessage(MessageUtil.parse("<red>Unknown or disabled Aspect id: " + aspectId + "</red>"));
            return true;
        }
        Aspect aspect = result.get();
        sender.sendMessage(MessageUtil.parse("<green>Set " + target.getName() + "'s Aspect to " + aspect.id() + ".</green>"));
        MessageUtil.sendMessage(target, plugin.getConfigManager().message("reroll.new"),
                Map.of("new", aspect.definition().displayName()));
        return true;
    }

    private boolean handleReroll(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aspects.admin.reroll")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>Usage: /aspects admin reroll <player></red>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse("<red>Player '" + args[1] + "' is not online.</red>"));
            return true;
        }
        Aspect previous = plugin.getAspectManager().getAspect(target.getUniqueId()).orElse(null);
        Optional<Aspect> result = plugin.getAspectManager().adminReroll(target);
        if (result.isEmpty()) {
            sender.sendMessage(MessageUtil.parse("<red>No eligible Aspect was available to reroll into.</red>"));
            return true;
        }
        Aspect newAspect = result.get();
        sender.sendMessage(MessageUtil.parse("<green>Rerolled " + target.getName() + "'s Aspect to " + newAspect.id() + ".</green>"));
        MessageUtil.sendMessage(target, plugin.getConfigManager().message("reroll.changed"));
        if (previous != null) {
            MessageUtil.sendMessage(target, plugin.getConfigManager().message("reroll.previous"),
                    Map.of("old", previous.definition().displayName()));
        }
        MessageUtil.sendMessage(target, plugin.getConfigManager().message("reroll.new"),
                Map.of("new", newAspect.definition().displayName()));
        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aspects.admin.reset")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>Usage: /aspects admin reset <player></red>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse("<red>Player '" + args[1] + "' is not online.</red>"));
            return true;
        }
        Optional<Aspect> result = plugin.getAspectManager().resetAspect(target);
        if (result.isPresent()) {
            Aspect aspect = result.get();
            sender.sendMessage(MessageUtil.parse(
                    "<green>Reset " + target.getName() + "'s Aspect — reassigned " + aspect.id() + ".</green>"));
            MessageUtil.sendMessage(target, plugin.getConfigManager().message("reroll.changed"));
            MessageUtil.sendMessage(target, plugin.getConfigManager().message("reroll.new"),
                    Map.of("new", aspect.definition().displayName()));
        } else {
            sender.sendMessage(MessageUtil.parse("<green>Reset " + target.getName() +
                    "'s Aspect — they will receive a new one on their next join.</green>"));
        }
        return true;
    }

    private boolean handleGiveReroller(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aspects.admin.give")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>Usage: /aspects admin give-reroller <player> [amount]</red>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse("<red>Player '" + args[1] + "' is not online.</red>"));
            return true;
        }
        int requested = 1;
        if (args.length >= 3) {
            try {
                requested = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(MessageUtil.parse("<red>'" + args[2] + "' is not a valid amount.</red>"));
                return true;
            }
        }
        int amount = clampGiveAmount(requested);
        CustomItemManager itemManager = plugin.getCustomItemManager();
        ItemStack stack = itemManager.createReroller();
        stack.setAmount(amount);
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            Location dropLocation = target.getLocation();
            World world = target.getWorld();
            for (ItemStack remaining : leftover.values()) {
                world.dropItemNaturally(dropLocation, remaining);
            }
            sender.sendMessage(MessageUtil.parse("<yellow>" + target.getName() +
                    "'s inventory was full — the remainder was dropped at their feet.</yellow>"));
        }
        plugin.getLogger().info(sender.getName() + " gave " + amount + " Aspect Reroller(s) to " + target.getName());
        sender.sendMessage(MessageUtil.parse("<green>Gave " + amount + " Aspect Reroller(s) to " + target.getName() + ".</green>"));
        return true;
    }

    static int clampGiveAmount(int requested) {
        if (requested < 1) {
            return 1;
        }
        return Math.min(requested, 64);
    }

    private boolean handleClearCooldown(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aspects.admin.cooldown")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>Usage: /aspects admin clear-cooldown <player></red>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse("<red>Player '" + args[1] + "' is not online.</red>"));
            return true;
        }
        plugin.getCooldownManager().clearCache(target.getUniqueId());
        sender.sendMessage(MessageUtil.parse("<green>Cleared all ability cooldowns for " + target.getName() + ".</green>"));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("aspects.admin.reload")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return true;
        }
        try {
            plugin.getConfigManager().loadAll();
        } catch (ConfigValidationException e) {
            sender.sendMessage(MessageUtil.parse("<red>Reload failed — configuration errors:</red>"));
            for (String error : e.errors()) {
                sender.sendMessage(MessageUtil.parse("<red> - " + error + "</red>"));
            }
            return true;
        }
        plugin.getRerollerRecipeManager().register(plugin.getConfigManager().recipes());
        sender.sendMessage(MessageUtil.parse("<green>Aspects configuration reloaded.</green>"));
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!sender.hasPermission("aspects.admin")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return true;
        }
        sender.sendMessage(MessageUtil.parse("<dark_purple>All Aspects:</dark_purple>"));
        for (Aspect aspect : plugin.getAspectRegistry().all()) {
            String status = aspect.definition().enabled() ? "<green>enabled</green>" : "<gray>disabled</gray>";
            sender.sendMessage(MessageUtil.parse("<white> - " + aspect.id() + "</white> (" + status + ")"));
        }
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aspects.admin.debug")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>Usage: /aspects admin debug <player></red>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse("<red>Player '" + args[1] + "' is not online.</red>"));
            return true;
        }
        Aspect aspect = plugin.getAspectManager().getAspect(target.getUniqueId()).orElse(null);
        sender.sendMessage(MessageUtil.parse("<dark_purple>Debug info for " + target.getName() + ":</dark_purple>"));
        sender.sendMessage(MessageUtil.parse("<white>Aspect: " + (aspect != null ? aspect.id() : "none") + "</white>"));
        if (aspect != null) {
            long remaining = plugin.getCooldownManager().remainingMillis(target.getUniqueId(), aspect.id());
            String cooldownText = remaining > 0 ? ((remaining + 999) / 1000) + "s remaining" : "ready";
            sender.sendMessage(MessageUtil.parse("<white>Cooldown: " + cooldownText + "</white>"));
        }
        sender.sendMessage(MessageUtil.parse("<white>exclude-current-aspect: " +
                plugin.getConfigManager().general().excludeCurrentOnReroll() + "</white>"));
        sender.sendMessage(MessageUtil.parse("<white>reset.immediate-reassign: " +
                plugin.getConfigManager().general().resetImmediateReassign() + "</white>"));
        return true;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q test -Dtest=AdminCommandTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Add the new permission nodes to `plugin.yml`**

Change:
```yaml
permissions:
  aspects.use:
    description: Use player Aspect commands and abilities.
    default: true
  aspects.admin:
    description: Access administrative Aspect commands.
    default: op
  aspects.admin.set:
    description: Assign a specific Aspect to a player.
    default: op
  aspects.admin.reroll:
    description: Force-reroll a player's Aspect.
    default: op
```
to:
```yaml
permissions:
  aspects.use:
    description: Use player Aspect commands and abilities.
    default: true
  aspects.gui:
    description: Open the player Aspects menu.
    default: true
  aspects.inspect:
    description: Inspect another player's Aspect.
    default: op
  aspects.admin:
    description: Access administrative Aspect commands.
    default: op
  aspects.admin.give:
    description: Give Aspect Reroller items or Aspect Selection tokens to players.
    default: op
  aspects.admin.set:
    description: Assign a specific Aspect to a player.
    default: op
  aspects.admin.reroll:
    description: Force-reroll a player's Aspect.
    default: op
  aspects.admin.reset:
    description: Reset a player's Aspect and related state.
    default: op
  aspects.admin.cooldown:
    description: Clear a player's ability cooldowns.
    default: op
  aspects.admin.reload:
    description: Reload the Aspects plugin configuration.
    default: op
  aspects.admin.debug:
    description: View diagnostic information about a player's Aspect state.
    default: op
```
(`aspects.gui` and `aspects.inspect` are used by Task 5, not this task — they're added here, alongside the
admin nodes, so all of this batch's permission additions land in one place. `aspects.admin.selector` is
intentionally NOT added here — it belongs to Batch 4b, which hasn't been planned yet.)

- [ ] **Step 6: Run the full test suite to confirm no regressions**

Run: `mvn -q test`
Expected: 85 tests total (80 before this task, plus these 5 new ones).

- [ ] **Step 7: Build the jar**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`, jar built.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/aspects/smp/command/AdminCommand.java src/test/java/com/aspects/smp/command/AdminCommandTest.java src/main/resources/plugin.yml
git commit -m "$(cat <<'EOF'
Add admin give-reroller, reset, clear-cooldown, reload, list, debug commands

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: `AspectsCommand` — `list`, `inspect`, `help`, plus full tab completion

**Files:**
- Modify: `src/main/java/com/aspects/smp/command/AspectsCommand.java`
- Modify: `src/main/java/com/aspects/smp/command/AspectsTabCompleter.java`

**Interfaces:**
- Consumes: `AspectRegistry.enabled()` (Phase 1/3); `AspectManager.getAspect` (Phase 1).
- Produces: nothing new for later tasks — this is the final integration point for Batch 4a.

- [ ] **Step 1: Read the current `src/main/java/com/aspects/smp/command/AspectsCommand.java`**

(It should have the `/aspects` → `MainAspectGUI` / `/aspects info` split from Phase 3 Batch 3b, and nothing
from Task 4 of this plan, since Task 4 only touched `AdminCommand.java`. If it looks different from what
this plan expects, stop and report BLOCKED rather than guessing.)

- [ ] **Step 2: Add the `Bukkit` import**

Add this import alongside the existing ones:
```java
import org.bukkit.Bukkit;
```

- [ ] **Step 3: Extend `onCommand` and add the three new handler methods**

Change:
```java
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            return adminCommand.handle(sender, shift(args));
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("info")) {
            return handleInfo(sender);
        }
        return handleOpenMenu(sender);
    }
```
to:
```java
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            return adminCommand.handle(sender, shift(args));
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("info")) {
            return handleInfo(sender);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            return handleList(sender);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("inspect")) {
            return handleInspect(sender, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            return handleHelp(sender);
        }
        return handleOpenMenu(sender);
    }

    private boolean handleList(CommandSender sender) {
        sender.sendMessage(MessageUtil.parse("<dark_purple>Enabled Aspects:</dark_purple>"));
        for (Aspect aspect : plugin.getAspectRegistry().enabled()) {
            sender.sendMessage(MessageUtil.parse(
                    "<" + aspect.definition().rarity().color() + ">" + aspect.definition().displayName() +
                            "</" + aspect.definition().rarity().color() + "> <gray>-</gray> " + aspect.definition().description()));
        }
        return true;
    }

    private boolean handleInspect(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aspects.inspect")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>Usage: /aspects inspect <player></red>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse("<red>Player '" + args[1] + "' is not online.</red>"));
            return true;
        }
        Aspect aspect = plugin.getAspectManager().getAspect(target.getUniqueId()).orElse(null);
        if (aspect == null) {
            sender.sendMessage(MessageUtil.parse("<red>" + target.getName() + " does not have an Aspect assigned yet.</red>"));
            return true;
        }
        sender.sendMessage(MessageUtil.parse(
                "<dark_purple>" + target.getName() + "'s Aspect:</dark_purple> " + aspect.definition().displayName()));
        sender.sendMessage(MessageUtil.parse(
                "<dark_purple>Rarity:</dark_purple> <" + aspect.definition().rarity().color() + ">" +
                        aspect.definition().rarity().name() + "</" + aspect.definition().rarity().color() + ">"));
        sender.sendMessage(MessageUtil.parse(
                "<dark_purple>Description:</dark_purple> <white>" + aspect.definition().description() + "</white>"));
        return true;
    }

    private boolean handleHelp(CommandSender sender) {
        sender.sendMessage(MessageUtil.parse("<dark_purple>Aspects commands:</dark_purple>"));
        sender.sendMessage(MessageUtil.parse("<white>/aspects</white> <gray>- Open the Aspects menu.</gray>"));
        sender.sendMessage(MessageUtil.parse("<white>/aspects info</white> <gray>- Show your current Aspect.</gray>"));
        sender.sendMessage(MessageUtil.parse("<white>/aspects list</white> <gray>- List all enabled Aspects.</gray>"));
        sender.sendMessage(MessageUtil.parse("<white>/aspects inspect <player></white> <gray>- View another player's Aspect.</gray>"));
        sender.sendMessage(MessageUtil.parse("<white>/aspects help</white> <gray>- Show this message.</gray>"));
        return true;
    }
```
(`handleInfo`, `handleOpenMenu`, and `shift` stay exactly as they already are — unchanged.)

- [ ] **Step 4: Replace the full contents of `AspectsTabCompleter.java`**

```java
package com.aspects.smp.command;

import com.aspects.smp.AspectsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class AspectsTabCompleter implements TabCompleter {
    private final AspectsPlugin plugin;

    public AspectsTabCompleter(AspectsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("info");
            options.add("list");
            options.add("inspect");
            options.add("help");
            options.add("admin");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("inspect")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                options.add(player.getName());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            options.add("set");
            options.add("reroll");
            options.add("reset");
            options.add("give-reroller");
            options.add("clear-cooldown");
            options.add("reload");
            options.add("list");
            options.add("debug");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin")
                && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("reroll")
                        || args[1].equalsIgnoreCase("reset") || args[1].equalsIgnoreCase("give-reroller")
                        || args[1].equalsIgnoreCase("clear-cooldown") || args[1].equalsIgnoreCase("debug"))) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                options.add(player.getName());
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("set")) {
            options.addAll(plugin.getConfigManager().aspectDefinitions().keySet());
        }

        String current = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        options.removeIf(opt -> !opt.toLowerCase().startsWith(current));
        return options;
    }
}
```

- [ ] **Step 5: Build and test**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`, all 85 tests pass, jar built.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/aspects/smp/command/AspectsCommand.java src/main/java/com/aspects/smp/command/AspectsTabCompleter.java
git commit -m "$(cat <<'EOF'
Add /aspects list, inspect, help commands and extend tab completion

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 7: Note for the controller, not this task's implementer**

Live verification against a running Paper 1.21.11 server is the controller's responsibility after this
task's review passes, the same way every prior batch's final wiring task was handled — do not attempt this
as the task implementer; there is no running server in that environment. Specifically verify: `/aspects
list` prints all 8 enabled Aspects with correct rarity colors; `/aspects inspect <player>` shows another
online player's Aspect (and is blocked without `aspects.inspect`); `/aspects help` prints the command list;
`/aspects admin give-reroller <player> 3` gives exactly 3 correctly-tagged Rerollers (and drops the
remainder if the inventory is full — test with a full inventory); `/aspects admin reset <player>` behaves
according to `reset.immediate-reassign` (test both `true`, the default, and `false` after a config edit +
`/aspects admin reload`); `/aspects admin clear-cooldown <player>` actually clears an active cooldown;
`/aspects admin reload` picks up a config edit without restarting and without duplicating the Reroller
recipe (craft two in a row after a reload to confirm no duplicate-registration error); `/aspects admin list`
shows all 8 Aspects as enabled; `/aspects admin debug <player>` shows sensible diagnostic output; tab
completion offers the right suggestions at every argument position for every new subcommand.
