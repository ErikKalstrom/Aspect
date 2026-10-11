# Aspects Plugin — Phase 5 (WorldGuard Integration) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional, soft-dependency WorldGuard integration: a dedicated `aspects-abilities` region flag
that, when set to `deny` in a WorldGuard region, blocks active-ability activation there and is also respected
by Void's teleport-destination search — with zero behavior change on any server that doesn't have WorldGuard
installed.

**Architecture:** One new class, `integration.WorldGuardIntegration`, isolates every WorldGuard/WorldEdit API
call behind a single `isAbilityAllowed(Location, Player)` method that always returns `true` when WorldGuard
isn't present. `ProtectionGate` (the existing single gate every active-ability activation already passes
through) and `VoidActiveAbility` (the only ability that teleports) both consult that one method — no other
file needs to know WorldGuard exists.

**Tech Stack:** `com.sk89q.worldguard:worldguard-bukkit:7.0.17` and its transitive `worldedit-bukkit:7.3.18`,
both Maven `provided` scope (compile-time only, never bundled into the shaded jar — confirmed by building with
the dependency added and observing the jar size is unchanged). Resolved from
`https://maven.enginehub.org/repo/`, confirmed reachable and resolving cleanly. **Version note:** 7.0.17 is
pinned deliberately, not just "a recent version" — 7.0.19's jars (and its transitive `worldedit` 7.4.4) are
compiled to Java 25 bytecode (class file major version 69), which fails to compile against this project's
Java 21 target (`mvn package` fails with "class file has wrong version 69.0, should be 65.0"). 7.0.17 (and
its transitive `worldedit` 7.3.18) are compiled to Java 21 bytecode (major version 65), matching this
project exactly — confirmed by inspecting the class file version bytes of the actual resolved jars, and by a
real `mvn package` compiling `WorldGuardIntegration.java` successfully against it. Do not bump this version
without re-checking the resolved jars' class file version against this project's `maven.compiler.target`.

## Global Constraints

- `WorldGuardIntegration` must never let a `NoClassDefFoundError` escape when WorldGuard isn't installed: its
  constructor checks `plugin.getServer().getPluginManager().getPlugin("WorldGuard")` (plain Bukkit API, safe
  without WorldGuard on the classpath at runtime) *before* touching any WorldGuard/WorldEdit class, and
  `isAbilityAllowed` returns `true` immediately if that check found nothing — no WorldGuard class is ever
  referenced in that path.
- `plugin.yml` declares `softdepend: [WorldGuard]` so that, when WorldGuard is installed, it is guaranteed to
  finish loading before `AspectsPlugin.onEnable()` runs — this is what makes registering the custom flag in
  our own `onEnable()` safe and is WorldGuard's own documented pattern for third-party flags.
  `provided`-scope Maven dependencies are compile-time only — do not change `worldguard-bukkit`'s scope to
  anything else, or it will be bundled into the shaded jar and create a hard dependency.
- The new flag is named exactly `aspects-abilities` (a `StateFlag`, default `ALLOW`) — do not reuse
  WorldGuard's built-in `PVP` flag (see the design doc's rationale: a region's PvP posture is independent of
  whether Aspect abilities should work there).
- This only gates **active** ability activation (`ProtectionGate`, consulted from `AbilityManager.tryActivate`)
  and Void's teleport-destination search. It must not touch passive-ability application
  (`PlayerJoinListener`/`PlayerRespawnListener`'s `reapplyPassive` calls) — passives are not "offensive
  abilities" per the spec and are out of scope here.
- No new permission nodes, commands, or GUI screens.
- Pure, Bukkit-API-free logic (the new `respect-worldguard` config flag's parsing) gets direct unit tests,
  exactly like every other `GeneralConfig` field. `WorldGuardIntegration`, `ProtectionGate`, and
  `VoidActiveAbility`'s extended destination check all require a live Bukkit `Player`/`World`/plugin registry
  and are manual-verification-only, consistent with every other Bukkit-API-dependent class in this codebase.

---

### Task 1: Add the WorldGuard dependency and the `WorldGuardIntegration` class

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/plugin.yml`
- Create: `src/main/java/com/aspects/smp/integration/WorldGuardIntegration.java`

**Interfaces:**
- Produces: `public WorldGuardIntegration(Plugin plugin)`, `public boolean isPresent()`,
  `public boolean isAbilityAllowed(Location location, Player player)` — the exact constructor and methods
  Task 3 (`AspectsPlugin`/`ProtectionGate`) and Task 4 (`VoidActiveAbility`) call.

- [ ] **Step 1: Add the Maven repository and dependency**

In `pom.xml`, inside the `<repositories>` block, add (after the existing `papermc` repository):

```xml
        <repository>
            <id>enginehub-maven</id>
            <url>https://maven.enginehub.org/repo/</url>
        </repository>
```

Inside the `<dependencies>` block, add (after the existing `sqlite-jdbc` dependency, before `junit-jupiter`):

```xml
        <dependency>
            <groupId>com.sk89q.worldguard</groupId>
            <artifactId>worldguard-bukkit</artifactId>
            <version>7.0.17</version>
            <scope>provided</scope>
        </dependency>
```

- [ ] **Step 2: Declare the soft dependency in `plugin.yml`**

In `src/main/resources/plugin.yml`, add one line after the `description:` line and before the blank line that
precedes `commands:`:

```yaml
softdepend: [WorldGuard]
```

The file's opening lines must read exactly:

```yaml
name: Aspects
version: '1.0.0-SNAPSHOT'
main: com.aspects.smp.AspectsPlugin
api-version: '1.21'
author: erikkalstrom
description: Elemental Aspect powers for SMP servers.
softdepend: [WorldGuard]

commands:
```

- [ ] **Step 3: Create `WorldGuardIntegration`**

```java
package com.aspects.smp.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class WorldGuardIntegration {
    private static final String FLAG_NAME = "aspects-abilities";

    private final boolean present;
    private StateFlag abilitiesFlag;

    public WorldGuardIntegration(Plugin plugin) {
        Plugin worldGuard = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        this.present = worldGuard != null && worldGuard.isEnabled();
        if (present) {
            registerFlag(plugin);
        }
    }

    public boolean isPresent() {
        return present;
    }

    public boolean isAbilityAllowed(Location location, Player player) {
        if (!present || abilitiesFlag == null) {
            return true;
        }
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        return query.testState(BukkitAdapter.adapt(location), localPlayer, abilitiesFlag);
    }

    private void registerFlag(Plugin plugin) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StateFlag flag = new StateFlag(FLAG_NAME, true);
            registry.register(flag);
            this.abilitiesFlag = flag;
        } catch (FlagConflictException e) {
            Flag<?> existing = registry.get(FLAG_NAME);
            if (existing instanceof StateFlag stateFlag) {
                this.abilitiesFlag = stateFlag;
            } else {
                plugin.getLogger().warning("Could not register the '" + FLAG_NAME +
                        "' WorldGuard flag: " + e.getMessage());
            }
        }
    }
}
```

(`isPresent()` has no caller yet in this plan — it exists for completeness and for a human operator or future
code to check WorldGuard's detected status; do not remove it as unused.)

- [ ] **Step 4: Build and verify**

```bash
export JAVA_HOME="/home/erikkalstrom/.local/share/flatpak/runtime/org.freedesktop.Sdk.Extension.openjdk21/x86_64/26.08/d4d21c831d4b2abcc5c2c1f32d7ac4a5cac886db0328a94a259021e26e93689a/files/jvm/openjdk-21"
export PATH="$JAVA_HOME/bin:/tmp/apache-maven-3.9.9/bin:$PATH"
mvn -q package
```
Expected: `BUILD SUCCESS`, all 84 existing tests still pass. The new dependency resolves from
`maven.enginehub.org` (requires network access during the build — confirmed reachable). No new tests are
added in this task (see Global Constraints).

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/resources/plugin.yml src/main/java/com/aspects/smp/integration/WorldGuardIntegration.java
git commit -m "$(cat <<'EOF'
Add WorldGuardIntegration as a soft-dependency region check

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Add the `respect-worldguard` config flag

**Files:**
- Modify: `src/main/java/com/aspects/smp/config/GeneralConfig.java`
- Modify: `src/main/java/com/aspects/smp/config/AspectsConfigParser.java`
- Modify: `src/main/resources/config.yml`
- Modify: `src/test/java/com/aspects/smp/config/AspectsConfigParserTest.java`

**Interfaces:**
- Produces: `GeneralConfig.respectWorldGuard() -> boolean`, read by Task 3's `AspectsPlugin` wiring.

- [ ] **Step 1: Add the field to `GeneralConfig`**

In `src/main/java/com/aspects/smp/config/GeneralConfig.java`, the record's full field list must read:

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
        boolean resetImmediateReassign,
        boolean respectWorldGuard
) {
}
```

(Only the new `boolean respectWorldGuard` line is added, as the last component, after `resetImmediateReassign`.)

- [ ] **Step 2: Parse the new key in `AspectsConfigParser`**

In `src/main/java/com/aspects/smp/config/AspectsConfigParser.java`, inside `parseGeneral`, add this line after
the existing `boolean resetImmediateReassign = yml.getBoolean("reset.immediate-reassign", true);` line:

```java
        boolean respectWorldGuard = yml.getBoolean("world-restrictions.respect-worldguard", true);
```

Then update the method's `return` statement — replace:

```java
        return new GeneralConfig(debug, sqliteFile, firstJoinEnabled, firstJoinSound,
                firstJoinParticle, firstJoinParticleCount, blockedWorlds, excludeCurrentOnReroll,
                revealAnimationEnabled, resetImmediateReassign);
```

with:

```java
        return new GeneralConfig(debug, sqliteFile, firstJoinEnabled, firstJoinSound,
                firstJoinParticle, firstJoinParticleCount, blockedWorlds, excludeCurrentOnReroll,
                revealAnimationEnabled, resetImmediateReassign, respectWorldGuard);
```

- [ ] **Step 3: Add the key to `config.yml`**

In `src/main/resources/config.yml`, the `world-restrictions:` section currently reads:

```yaml
world-restrictions:
  blocked-worlds: []
```

Change it to:

```yaml
world-restrictions:
  blocked-worlds: []
  respect-worldguard: true
```

- [ ] **Step 4: Write the failing tests**

Append to `src/test/java/com/aspects/smp/config/AspectsConfigParserTest.java`, immediately before the final
closing `}` of the class (after the existing `parseGeneral_readsResetImmediateReassignFalse` test):

```java

    @Test
    void parseGeneral_defaultsRespectWorldGuardToTrue() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(""));
        List<String> errors = new ArrayList<>();

        GeneralConfig config = AspectsConfigParser.parseGeneral(yml, errors);

        assertTrue(config.respectWorldGuard());
    }

    @Test
    void parseGeneral_readsRespectWorldGuardFalse() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "world-restrictions:\n  respect-worldguard: false\n"));
        List<String> errors = new ArrayList<>();

        GeneralConfig config = AspectsConfigParser.parseGeneral(yml, errors);

        assertFalse(config.respectWorldGuard());
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

```bash
export JAVA_HOME="/home/erikkalstrom/.local/share/flatpak/runtime/org.freedesktop.Sdk.Extension.openjdk21/x86_64/26.08/d4d21c831d4b2abcc5c2c1f32d7ac4a5cac886db0328a94a259021e26e93689a/files/jvm/openjdk-21"
export PATH="$JAVA_HOME/bin:/tmp/apache-maven-3.9.9/bin:$PATH"
mvn -q package
```
Expected: `BUILD SUCCESS`, 86 tests total (84 existing + 2 new), all passing. (If you ran the two new tests
before adding the `GeneralConfig` field and parser line, they would fail to compile — that is the expected
RED state for this transcription task; GREEN is this full build succeeding after all steps above.)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/aspects/smp/config/GeneralConfig.java src/main/java/com/aspects/smp/config/AspectsConfigParser.java src/main/resources/config.yml src/test/java/com/aspects/smp/config/AspectsConfigParserTest.java
git commit -m "$(cat <<'EOF'
Add world-restrictions.respect-worldguard config flag

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Wire `WorldGuardIntegration` into `ProtectionGate` and `AspectsPlugin`

**Files:**
- Modify: `src/main/java/com/aspects/smp/ability/ProtectionGate.java`
- Modify: `src/main/java/com/aspects/smp/AspectsPlugin.java`

**Interfaces:**
- Consumes: `WorldGuardIntegration(Plugin)` and `WorldGuardIntegration.isAbilityAllowed(Location, Player)` from
  Task 1; `GeneralConfig.respectWorldGuard()` from Task 2.
- Produces: `AspectsPlugin.getWorldGuardIntegration() -> WorldGuardIntegration`, which Task 4
  (`VoidActiveAbility`) calls.

- [ ] **Step 1: Update `ProtectionGate`**

Replace the full contents of `src/main/java/com/aspects/smp/ability/ProtectionGate.java` with:

```java
package com.aspects.smp.ability;

import com.aspects.smp.integration.WorldGuardIntegration;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class ProtectionGate {
    private final Set<String> blockedWorlds;
    private final WorldGuardIntegration worldGuardIntegration;
    private final boolean respectWorldGuard;

    public ProtectionGate(Collection<String> blockedWorlds, WorldGuardIntegration worldGuardIntegration,
                           boolean respectWorldGuard) {
        this.blockedWorlds = new HashSet<>(blockedWorlds);
        this.worldGuardIntegration = worldGuardIntegration;
        this.respectWorldGuard = respectWorldGuard;
    }

    public boolean isAllowed(Player player) {
        if (blockedWorlds.contains(player.getWorld().getName())) {
            return false;
        }
        if (respectWorldGuard && !worldGuardIntegration.isAbilityAllowed(player.getLocation(), player)) {
            return false;
        }
        return true;
    }
}
```

- [ ] **Step 2: Wire it into `AspectsPlugin`**

In `src/main/java/com/aspects/smp/AspectsPlugin.java`, add this import in alphabetical position — after the
existing `import com.aspects.smp.data.SQLitePlayerDataRepository;` line and before
`import com.aspects.smp.item.AspectSelectorInteractListener;`:

```java
import com.aspects.smp.integration.WorldGuardIntegration;
```

Add a new field after the existing `private AbilityManager abilityManager;` field declaration:

```java
    private WorldGuardIntegration worldGuardIntegration;
```

In `onEnable()`, replace the current line:

```java
        ProtectionGate protectionGate = new ProtectionGate(configManager.general().blockedWorlds());
```

with:

```java
        worldGuardIntegration = new WorldGuardIntegration(this);
        ProtectionGate protectionGate = new ProtectionGate(configManager.general().blockedWorlds(),
                worldGuardIntegration, configManager.general().respectWorldGuard());
```

Add a new getter, following the existing pattern, after the existing `getRerollerRecipeManager()` method
(immediately before the class's final closing `}`):

```java

    public WorldGuardIntegration getWorldGuardIntegration() {
        return worldGuardIntegration;
    }
```

- [ ] **Step 3: Build and verify**

```bash
export JAVA_HOME="/home/erikkalstrom/.local/share/flatpak/runtime/org.freedesktop.Sdk.Extension.openjdk21/x86_64/26.08/d4d21c831d4b2abcc5c2c1f32d7ac4a5cac886db0328a94a259021e26e93689a/files/jvm/openjdk-21"
export PATH="$JAVA_HOME/bin:/tmp/apache-maven-3.9.9/bin:$PATH"
mvn -q package
```
Expected: `BUILD SUCCESS`, all 86 tests (from Task 2) still pass. No new tests are added in this task — both
modified files require a live Bukkit `Player`/`Plugin` and are manual-verification-only (see Global
Constraints).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/aspects/smp/ability/ProtectionGate.java src/main/java/com/aspects/smp/AspectsPlugin.java
git commit -m "$(cat <<'EOF'
Gate active-ability activation through WorldGuardIntegration

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Extend Void's teleport-destination search to respect the flag

**Files:**
- Modify: `src/main/java/com/aspects/smp/ability/impl/VoidActiveAbility.java`

**Interfaces:**
- Consumes: `AspectsPlugin.getWorldGuardIntegration()` from Task 3; `WorldGuardIntegration.isAbilityAllowed(Location, Player)` from Task 1.

- [ ] **Step 1: Update the destination search**

Replace the full contents of `src/main/java/com/aspects/smp/ability/impl/VoidActiveAbility.java` with:

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

        IntPredicate isSafeAtDistance = distance -> isSafeDestination(context, world, origin, horizontal, distance);
        int safeDistance = pickSafeDistance(maxDistance, isSafeAtDistance);
        if (safeDistance == 0) {
            return false;
        }

        double destX = Math.floor(origin.getX() + horizontal.getX() * safeDistance) + 0.5;
        double destY = Math.floor(origin.getY() + horizontal.getY() * safeDistance);
        double destZ = Math.floor(origin.getZ() + horizontal.getZ() * safeDistance) + 0.5;
        Location destination = new Location(world, destX, destY, destZ, origin.getYaw(), origin.getPitch());

        ParticleUtil.spawn(origin, "PORTAL", 30, context.plugin().getLogger());
        boolean teleported = player.teleport(destination);
        if (!teleported) {
            return false;
        }
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

    private boolean isSafeDestination(AbilityContext context, World world, Location origin, Vector horizontal, int distance) {
        Location point = origin.clone().add(horizontal.clone().multiply(distance));
        if (!world.getWorldBorder().isInside(point)) {
            return false;
        }
        int y = point.getBlockY();
        if (y - 1 < world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
            return false;
        }
        int bx = point.getBlockX();
        int bz = point.getBlockZ();
        Material feet = world.getBlockAt(bx, y, bz).getType();
        Material head = world.getBlockAt(bx, y + 1, bz).getType();
        Material floor = world.getBlockAt(bx, y - 1, bz).getType();
        if (feet.isSolid() || head.isSolid() || feet == Material.LAVA || head == Material.LAVA || floor == Material.LAVA) {
            return false;
        }
        return context.plugin().getWorldGuardIntegration().isAbilityAllowed(point, context.player());
    }
}
```

The only changes from the existing file: `isSafeDestination` now takes `AbilityContext context` as its first
parameter (passed through from the lambda in `activate`), its final `return` statement is restructured so the
existing block/lava checks short-circuit with an early `return false` (unchanged logic, just reshaped to make
room for the new check), and the method's last line now also requires
`context.plugin().getWorldGuardIntegration().isAbilityAllowed(point, context.player())` to be `true`.

- [ ] **Step 2: Check for existing tests on this file**

Run:
```bash
grep -rln "VoidActiveAbility\|pickSafeDistance" src/test/
```
`pickSafeDistance` is a package-private static method with its own existing unit test(s) (it takes an
`IntPredicate`, not a `WorldGuardIntegration`, so it is unaffected by this change and needs no test updates).
If this grep finds a test file, read it and confirm it still compiles and passes after this change — it
should, since `pickSafeDistance`'s signature is untouched. If it does not compile, report BLOCKED with the
exact compile error rather than modifying the test to work around it.

- [ ] **Step 3: Build and verify**

```bash
export JAVA_HOME="/home/erikkalstrom/.local/share/flatpak/runtime/org.freedesktop.Sdk.Extension.openjdk21/x86_64/26.08/d4d21c831d4b2abcc5c2c1f32d7ac4a5cac886db0328a94a259021e26e93689a/files/jvm/openjdk-21"
export PATH="$JAVA_HOME/bin:/tmp/apache-maven-3.9.9/bin:$PATH"
mvn -q package
```
Expected: `BUILD SUCCESS`, all 86 tests still pass (no new tests added in this task; the one pre-existing
`pickSafeDistance` test(s) must still pass unchanged).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/aspects/smp/ability/impl/VoidActiveAbility.java
git commit -m "$(cat <<'EOF'
Make Void's teleport destination search respect the WorldGuard flag

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

## Batch-level manual verification (after all 4 tasks)

This batch needs WorldGuard actually installed in the `aspects-test` Docker container, which none of the
prior batches required — set this up first:

1. Download WorldGuard and WorldEdit's Bukkit plugin jars matching Paper 1.21.11 (check
   `https://dev.bukkit.org/projects/worldguard` / the EngineHub downloads page for the latest 7.0.x WorldGuard
   build and its required WorldEdit version), `docker cp` both into `/data/plugins/` alongside the Aspects
   jar, and restart the container. Confirm via `docker logs aspects-test` that both plugins report
   "Enabled" with no errors, and that the Aspects plugin's own startup logs show no WorldGuard-related
   warnings (specifically: no "Could not register the 'aspects-abilities' WorldGuard flag" warning — that
   would indicate a real registration failure to investigate, not a success).
2. Confirm the flag registered: `/rg flag __global__ aspects-abilities` (as an op) should report the flag
   exists and currently reads its default (unset, meaning value `ALLOW` applies in the absence of a more
   specific region's own setting).
3. Define a test region covering a small area: `//wand`, select two corners, `/rg define abilitytest`.
4. Inside that region, set the flag to deny: `/rg flag abilitytest aspects-abilities deny`.
5. Give a test bot an Aspect whose active ability doesn't teleport (e.g. Frost or Earth), stand them inside
   `abilitytest`, and trigger the ability (sneak + swap-hands, per the existing activation mechanism) —
   confirm it is refused with the `ability.world-blocked` message (reusing the same action-bar message
   blocked-worlds already uses), and that cooldown is NOT started (since activation never reached that point).
6. Move the bot just outside the region and confirm the same ability now activates normally.
7. Set the flag back to `allow` (or unset it with `/rg flag abilitytest aspects-abilities -`) and confirm the
   ability now works inside the region too — proving the flag genuinely gates behavior both ways, not just
   failing safe in one direction by coincidence.
8. Assign the Void Aspect to the test bot. Create a region directly in front of where the bot is facing, close
   enough to be within Void's `active.max-distance` (default 7 blocks), with `aspects-abilities` denied. Have
   the bot activate Void's teleport — confirm it either lands short of the denied region (if a safe, allowed
   spot exists at a shorter distance) or the ability fails outright with no teleport (if every distance up to
   max is inside the denied region) — never confirm it landing inside the denied region.
9. As a control, repeat step 8 with no `aspects-abilities` restriction anywhere nearby and confirm Void
   teleports normally, to rule out a regression from this change to the existing block/lava/border logic.
10. Set `world-restrictions.respect-worldguard: false` in `config.yml`, reload (`/aspects admin reload` — note
    per Phase 4 Batch 4a's existing reload limitation, `config.yml` general flags require a server restart to
    take effect live, so actually restart the container here), and confirm abilities now work inside the
    denied region too — proving the opt-out config flag genuinely disables the integration.
11. As a final control, stop the WorldGuard/WorldEdit plugins (remove their jars, restart), and confirm the
    Aspects plugin still starts cleanly with no errors and every ability still works normally everywhere —
    proving the soft dependency truly imposes no requirement.
12. Log the full verdict to `.superpowers/sdd/progress.md`, matching the detail level of every prior batch's
    live-verification ledger entry.

## What's deliberately out of scope here

No `build`/`block-place`/chest-protection flags, no general WorldGuard region-membership commands or GUI
surfacing region info to players, no bypass-permission logic of our own (WorldGuard's own
`worldguard.region.bypass.*` permission is already honored by `RegionQuery.testState` for free). This is the
final phase from the original functional spec — anything beyond this is net-new scope, not part of Phase 5.
