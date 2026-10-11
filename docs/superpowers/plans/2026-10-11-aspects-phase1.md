# Aspects Plugin — Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first working vertical slice of the Aspects Paper plugin: project scaffold, config system, SQLite persistence, the Aspect registry/assignment/ability framework, and two fully-implemented Aspects (Fire, Tide), wired together and manually verifiable in-game.

**Architecture:** See `docs/superpowers/specs/2026-10-11-aspects-plugin-design.md` for the full package layout and rationale. This plan implements that design's Phase 1 scope only.

**Tech Stack:** Java 21, Maven, Paper API `1.21.11-R0.1-SNAPSHOT` (scope `provided`), Adventure/MiniMessage for text, `org.xerial:sqlite-jdbc` (shaded) for persistence, JUnit 5 for unit tests.

## Global Constraints

- Minecraft/Paper target: **1.21.11** — confirmed live against the actual client by the user. Paper API coordinate: `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`, scope `provided`. Verified against PaperMC's own docs/news (Aug 2026 release) — not a guess.
- Java: **21** (confirmed as 1.21.11's required Java version via PaperMC docs/forums).
- Build: Maven, repository `https://repo.papermc.io/repository/maven-public/`.
- Package: `com.aspects.smp`. Main class: `com.aspects.smp.AspectsPlugin`.
- Storage: **SQLite only**, no YAML toggle (explicit decision — simpler single code path).
- Attribute API: `Attribute` is an interface in 1.21.11, not an enum. Use `Attribute.MAX_HEALTH` (the old `GENERIC_MAX_HEALTH` enum constant was removed with no compatibility shim).
- Sound API: `Sound` is an interface in 1.21.11. Look sounds up via `Registry.SOUNDS.get(NamespacedKey.minecraft(name))`, never `Sound.valueOf(...)` (does not compile — no static `valueOf` on an interface).
- Particle API: `Particle` remains a plain enum in 1.21.11 — `Particle.valueOf(name)` is valid.
- `PotionEffectType.FIRE_RESISTANCE` and `PotionEffectType.WATER_BREATHING` are unchanged static fields — safe to reference directly.
- Any Bukkit API call that mutates a live entity/world state (potion effects, damage, teleports, messages) must run on the main server thread. All SQLite I/O must run off the main thread.
- Never claim in-game behavior is verified unless it was actually tested on a running server. Unit tests verify pure logic only; this plan calls out explicitly which parts need manual server verification.
- Nothing in this phase hides unimplemented scope: the six aspects not built this phase (Frost, Thunder, Void, Earth, Wind, Blood) ship as explicitly `enabled: false` stub entries with a `description` saying they are not yet implemented.

---

### Task 1: Maven & Paper plugin scaffold

**Files:**
- Create: `pom.xml`
- Create: `src/main/resources/plugin.yml`
- Create: `src/main/java/com/aspects/smp/AspectsPlugin.java`
- Create: `.gitignore`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: `com.aspects.smp.AspectsPlugin` (a `JavaPlugin` subclass with empty `onEnable`/`onDisable` for now) — every later task adds fields/wiring to this class.

- [ ] **Step 1: Create `.gitignore`**

```
target/
*.class
*.db
.idea/
*.iml
```

- [ ] **Step 2: Create `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.aspects.smp</groupId>
    <artifactId>aspects</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <repositories>
        <repository>
            <id>papermc</id>
            <url>https://repo.papermc.io/repository/maven-public/</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>io.papermc.paper</groupId>
            <artifactId>paper-api</artifactId>
            <version>1.21.11-R0.1-SNAPSHOT</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.xerial</groupId>
            <artifactId>sqlite-jdbc</artifactId>
            <version>3.53.4.0</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.0</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>${project.artifactId}-${project.version}</finalName>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.6.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <relocations>
                                <relocation>
                                    <pattern>org.sqlite</pattern>
                                    <shadedPattern>com.aspects.smp.libs.sqlite</shadedPattern>
                                </relocation>
                            </relocations>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: Create `src/main/resources/plugin.yml`**

```yaml
name: Aspects
version: '1.0.0-SNAPSHOT'
main: com.aspects.smp.AspectsPlugin
api-version: '1.21'
author: erikkalstrom
description: Elemental Aspect powers for SMP servers.

commands:
  aspects:
    description: Manage your Aspect.
    aliases: [aspect]
    permission: aspects.use

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

- [ ] **Step 4: Create `src/main/java/com/aspects/smp/AspectsPlugin.java`**

```java
package com.aspects.smp;

import org.bukkit.plugin.java.JavaPlugin;

public final class AspectsPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Aspects scaffold enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Aspects disabled.");
    }
}
```

- [ ] **Step 5: Build and verify**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`, and `target/aspects-1.0.0-SNAPSHOT.jar` exists.

- [ ] **Step 6: Commit**

```bash
git add pom.xml .gitignore src/main/resources/plugin.yml src/main/java/com/aspects/smp/AspectsPlugin.java
git commit -m "$(cat <<'EOF'
Scaffold Aspects Maven project against Paper API 1.21.11

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: MessageUtil

**Files:**
- Create: `src/main/java/com/aspects/smp/util/MessageUtil.java`
- Test: `src/test/java/com/aspects/smp/util/MessageUtilTest.java`

**Interfaces:**
- Consumes: nothing new (Adventure/MiniMessage ships with paper-api).
- Produces: `MessageUtil.parse(String)`, `MessageUtil.parse(String, Map<String,String>)`, `MessageUtil.sendMessage(Player, String)`, `MessageUtil.sendMessage(Player, String, Map<String,String>)`, `MessageUtil.sendActionBar(Player, String)`, `MessageUtil.sendActionBar(Player, String, Map<String,String>)` — used by every later task that sends player-facing text.

- [ ] **Step 1: Write the failing test**

```java
package com.aspects.smp.util;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageUtilTest {

    @Test
    void parse_rendersMiniMessageTagsAsPlainText() {
        var component = MessageUtil.parse("<red>Hello</red>");
        assertEquals("Hello", PlainTextComponentSerializer.plainText().serialize(component));
    }

    @Test
    void parse_substitutesPlaceholdersBeforeDeserializing() {
        var component = MessageUtil.parse("<white>Aspect: {aspect}</white>", Map.of("aspect", "Fire"));
        assertEquals("Aspect: Fire", PlainTextComponentSerializer.plainText().serialize(component));
    }

    @Test
    void parse_handlesNullGracefully() {
        var component = MessageUtil.parse(null);
        assertEquals("", PlainTextComponentSerializer.plainText().serialize(component));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=MessageUtilTest`
Expected: FAIL — `MessageUtil` does not exist (compilation error).

- [ ] **Step 3: Write `src/main/java/com/aspects/smp/util/MessageUtil.java`**

```java
package com.aspects.smp.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.Map;

public final class MessageUtil {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private MessageUtil() {}

    public static Component parse(String raw) {
        return MINI_MESSAGE.deserialize(raw == null ? "" : raw);
    }

    public static Component parse(String raw, Map<String, String> placeholders) {
        String result = raw == null ? "" : raw;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return MINI_MESSAGE.deserialize(result);
    }

    public static void sendMessage(Player player, String raw) {
        player.sendMessage(parse(raw));
    }

    public static void sendMessage(Player player, String raw, Map<String, String> placeholders) {
        player.sendMessage(parse(raw, placeholders));
    }

    public static void sendActionBar(Player player, String raw) {
        player.sendActionBar(parse(raw));
    }

    public static void sendActionBar(Player player, String raw, Map<String, String> placeholders) {
        player.sendActionBar(parse(raw, placeholders));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=MessageUtilTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/aspects/smp/util/MessageUtil.java src/test/java/com/aspects/smp/util/MessageUtilTest.java
git commit -m "$(cat <<'EOF'
Add MessageUtil for MiniMessage parsing and placeholder substitution

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: General config model and parser

**Files:**
- Create: `src/main/resources/config.yml`
- Create: `src/main/java/com/aspects/smp/config/GeneralConfig.java`
- Create: `src/main/java/com/aspects/smp/config/AspectsConfigParser.java` (general-config half only; aspects half added in Task 4)
- Test: `src/test/java/com/aspects/smp/config/AspectsConfigParserTest.java` (general-config tests only; aspects tests added in Task 4)

**Interfaces:**
- Consumes: nothing new.
- Produces: `GeneralConfig` record, `AspectsConfigParser.parseGeneral(FileConfiguration, List<String> errors) -> GeneralConfig` — consumed by `ConfigManager` in Task 5.

- [ ] **Step 1: Create `src/main/resources/config.yml`**

```yaml
# Aspects plugin — general settings.
general:
  debug: false

storage:
  sqlite-file: "playerdata.db"

first-join:
  enabled: true
  sound: "entity.player.levelup"
  particle: "TOTEM"
  particle-count: 20

world-restrictions:
  blocked-worlds: []

reroll:
  exclude-current-aspect: true
```

- [ ] **Step 2: Write the failing test**

```java
package com.aspects.smp.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AspectsConfigParserTest {

    @Test
    void parseGeneral_acceptsValidValues() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "storage:\n  sqlite-file: data.db\nfirst-join:\n  particle-count: 10\n"));
        List<String> errors = new ArrayList<>();

        GeneralConfig config = AspectsConfigParser.parseGeneral(yml, errors);

        assertTrue(errors.isEmpty());
        assertEquals("data.db", config.sqliteFile());
        assertEquals(10, config.firstJoinParticleCount());
    }

    @Test
    void parseGeneral_rejectsNegativeParticleCount() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "first-join:\n  particle-count: -5\n"));
        List<String> errors = new ArrayList<>();

        AspectsConfigParser.parseGeneral(yml, errors);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("particle-count"));
    }

    @Test
    void parseGeneral_rejectsBlankSqliteFile() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "storage:\n  sqlite-file: \"\"\n"));
        List<String> errors = new ArrayList<>();

        GeneralConfig config = AspectsConfigParser.parseGeneral(yml, errors);

        assertEquals(1, errors.size());
        assertEquals("playerdata.db", config.sqliteFile());
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q test -Dtest=AspectsConfigParserTest`
Expected: FAIL — `GeneralConfig`/`AspectsConfigParser` do not exist.

- [ ] **Step 4: Create `src/main/java/com/aspects/smp/config/GeneralConfig.java`**

```java
package com.aspects.smp.config;

import java.util.List;

public record GeneralConfig(
        boolean debug,
        String sqliteFile,
        boolean firstJoinEnabled,
        String firstJoinSound,
        String firstJoinParticle,
        int firstJoinParticleCount,
        List<String> blockedWorlds,
        boolean excludeCurrentOnReroll
) {
}
```

- [ ] **Step 5: Create `src/main/java/com/aspects/smp/config/AspectsConfigParser.java`**

```java
package com.aspects.smp.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public final class AspectsConfigParser {
    private AspectsConfigParser() {}

    public static GeneralConfig parseGeneral(FileConfiguration yml, List<String> errors) {
        boolean debug = yml.getBoolean("general.debug", false);
        String sqliteFile = yml.getString("storage.sqlite-file", "playerdata.db");
        boolean firstJoinEnabled = yml.getBoolean("first-join.enabled", true);
        String firstJoinSound = yml.getString("first-join.sound", "entity.player.levelup");
        String firstJoinParticle = yml.getString("first-join.particle", "TOTEM");
        int firstJoinParticleCount = yml.getInt("first-join.particle-count", 20);
        List<String> blockedWorlds = yml.getStringList("world-restrictions.blocked-worlds");
        boolean excludeCurrentOnReroll = yml.getBoolean("reroll.exclude-current-aspect", true);

        if (sqliteFile == null || sqliteFile.isBlank()) {
            errors.add("storage.sqlite-file must not be blank");
            sqliteFile = "playerdata.db";
        }
        if (firstJoinParticleCount < 0) {
            errors.add("first-join.particle-count must not be negative");
        }

        return new GeneralConfig(debug, sqliteFile, firstJoinEnabled, firstJoinSound,
                firstJoinParticle, firstJoinParticleCount, blockedWorlds, excludeCurrentOnReroll);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q test -Dtest=AspectsConfigParserTest`
Expected: PASS (3 tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/config.yml src/main/java/com/aspects/smp/config/GeneralConfig.java src/main/java/com/aspects/smp/config/AspectsConfigParser.java src/test/java/com/aspects/smp/config/AspectsConfigParserTest.java
git commit -m "$(cat <<'EOF'
Add config.yml and the general-settings parser with validation

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Aspect rarity, definition, and aspects.yml parser

**Files:**
- Create: `src/main/resources/aspects.yml`
- Create: `src/main/java/com/aspects/smp/aspect/AspectRarity.java`
- Create: `src/main/java/com/aspects/smp/aspect/AspectDefinition.java`
- Modify: `src/main/java/com/aspects/smp/config/AspectsConfigParser.java` (add `parseAspects`)
- Modify: `src/test/java/com/aspects/smp/config/AspectsConfigParserTest.java` (add aspect-parsing tests)

**Interfaces:**
- Consumes: `AspectsConfigParser` from Task 3.
- Produces: `AspectRarity` enum (`COMMON, UNCOMMON, RARE, EPIC, LEGENDARY`); `AspectDefinition` record with `id(), displayName(), description(), rarity(), weight(), enabled(), settings()` plus `getDouble(key, default)`, `getInt(key, default)`, `getBoolean(key, default)`; `AspectsConfigParser.parseAspects(FileConfiguration, List<String>) -> Map<String, AspectDefinition>`. Consumed by `ConfigManager` (Task 5), `AspectRegistry`/`AspectAssignmentService` (Task 8), and both ability implementations (Tasks 10–11).

- [ ] **Step 1: Create `src/main/resources/aspects.yml`**

```yaml
# Each entry under `aspects` defines one Aspect. `enabled: false` aspects are
# never assigned to players and are excluded from random selection.
#
# fire and tide are the only two Aspects implemented in this phase (Phase 1).
# The remaining six are listed here with their final rarity/description so the
# file's shape is correct, but their abilities are NOT implemented yet — they
# ship disabled until a later phase adds their code.
aspects:
  fire:
    enabled: true
    display-name: "<red>Fire</red>"
    description: "Aggressive damage and burning enemies."
    rarity: RARE
    weight: 20
    passive:
      fire-resistance: true
    active:
      cooldown-seconds: 25
      radius: 4.0
      damage: 4.0
      burn-ticks: 60
      windup-ticks: 10

  tide:
    enabled: true
    display-name: "<aqua>Tide</aqua>"
    description: "Water, regeneration, and endurance."
    rarity: COMMON
    weight: 40
    passive:
      water-breathing: true
    active:
      cooldown-seconds: 35
      heal-amount: 4.0

  frost:
    enabled: false
    display-name: "<aqua>Frost</aqua>"
    description: "Not yet implemented (planned for Phase 2)."
    rarity: RARE
    weight: 0

  thunder:
    enabled: false
    display-name: "<yellow>Thunder</yellow>"
    description: "Not yet implemented (planned for Phase 2)."
    rarity: EPIC
    weight: 0

  void:
    enabled: false
    display-name: "<dark_purple>Void</dark_purple>"
    description: "Not yet implemented (planned for Phase 2)."
    rarity: LEGENDARY
    weight: 0

  earth:
    enabled: false
    display-name: "<dark_green>Earth</dark_green>"
    description: "Not yet implemented (planned for Phase 2)."
    rarity: RARE
    weight: 0

  wind:
    enabled: false
    display-name: "<white>Wind</white>"
    description: "Not yet implemented (planned for Phase 2)."
    rarity: COMMON
    weight: 0

  blood:
    enabled: false
    display-name: "<dark_red>Blood</dark_red>"
    description: "Not yet implemented (planned for Phase 2)."
    rarity: EPIC
    weight: 0
```

- [ ] **Step 2: Write the failing tests (append to `AspectsConfigParserTest.java`)**

Add these methods inside the existing `AspectsConfigParserTest` class, and add `import com.aspects.smp.aspect.AspectDefinition;` and `import java.util.Map;` to its imports:

```java
    @Test
    void parseAspects_rejectsUnknownRarityButStillReturnsDefinition() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "aspects:\n  fire:\n    enabled: true\n    rarity: NOT_A_RARITY\n    weight: 10\n"));
        List<String> errors = new ArrayList<>();

        Map<String, AspectDefinition> definitions = AspectsConfigParser.parseAspects(yml, errors);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("rarity"));
        assertTrue(definitions.containsKey("fire"));
    }

    @Test
    void parseAspects_rejectsNegativeWeight() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "aspects:\n  fire:\n    enabled: true\n    rarity: RARE\n    weight: -3\n"));
        List<String> errors = new ArrayList<>();

        AspectsConfigParser.parseAspects(yml, errors);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("weight"));
    }

    @Test
    void parseAspects_flattensNestedSettingsWithDottedKeys() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "aspects:\n  fire:\n    enabled: true\n    rarity: RARE\n    weight: 10\n" +
                        "    active:\n      cooldown-seconds: 25\n      radius: 4.0\n"));
        List<String> errors = new ArrayList<>();

        Map<String, AspectDefinition> definitions = AspectsConfigParser.parseAspects(yml, errors);

        assertTrue(errors.isEmpty());
        AspectDefinition fire = definitions.get("fire");
        assertEquals(25.0, fire.getDouble("active.cooldown-seconds", -1));
        assertEquals(4.0, fire.getDouble("active.radius", -1));
    }

    @Test
    void parseAspects_missingSectionProducesError() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader("other-key: true\n"));
        List<String> errors = new ArrayList<>();

        AspectsConfigParser.parseAspects(yml, errors);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("aspects"));
    }
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -q test -Dtest=AspectsConfigParserTest`
Expected: FAIL — `AspectDefinition`/`AspectsConfigParser.parseAspects` do not exist.

- [ ] **Step 4: Create `src/main/java/com/aspects/smp/aspect/AspectRarity.java`**

```java
package com.aspects.smp.aspect;

public enum AspectRarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY
}
```

- [ ] **Step 5: Create `src/main/java/com/aspects/smp/aspect/AspectDefinition.java`**

```java
package com.aspects.smp.aspect;

import java.util.Map;

public record AspectDefinition(
        String id,
        String displayName,
        String description,
        AspectRarity rarity,
        double weight,
        boolean enabled,
        Map<String, Object> settings
) {

    public double getDouble(String key, double defaultValue) {
        Object value = settings.get(key);
        return (value instanceof Number number) ? number.doubleValue() : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        Object value = settings.get(key);
        return (value instanceof Number number) ? number.intValue() : defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = settings.get(key);
        return (value instanceof Boolean bool) ? bool : defaultValue;
    }
}
```

- [ ] **Step 6: Add `parseAspects` to `AspectsConfigParser.java`**

Add these imports to the top of the file:

```java
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.aspect.AspectRarity;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
```

Add this method to the `AspectsConfigParser` class:

```java
    public static Map<String, AspectDefinition> parseAspects(FileConfiguration yml, List<String> errors) {
        Map<String, AspectDefinition> result = new LinkedHashMap<>();
        ConfigurationSection section = yml.getConfigurationSection("aspects");
        if (section == null) {
            errors.add("aspects.yml is missing the top-level 'aspects' section");
            return result;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection aspectSection = section.getConfigurationSection(id);
            if (aspectSection == null) {
                continue;
            }

            boolean enabled = aspectSection.getBoolean("enabled", false);
            String displayName = aspectSection.getString("display-name", id);
            String description = aspectSection.getString("description", "");
            String rarityRaw = aspectSection.getString("rarity", "COMMON");
            double weight = aspectSection.getDouble("weight", 0);

            AspectRarity rarity;
            try {
                rarity = AspectRarity.valueOf(rarityRaw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.add("aspects." + id + ".rarity has unknown value '" + rarityRaw + "'");
                rarity = AspectRarity.COMMON;
            }
            if (weight < 0) {
                errors.add("aspects." + id + ".weight must not be negative");
                weight = 0;
            }

            Map<String, Object> settings = aspectSection.getValues(true);
            result.put(id, new AspectDefinition(id, displayName, description, rarity, weight, enabled, settings));
        }
        return result;
    }
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `mvn -q test -Dtest=AspectsConfigParserTest`
Expected: PASS (7 tests total).

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/aspects.yml src/main/java/com/aspects/smp/aspect/AspectRarity.java src/main/java/com/aspects/smp/aspect/AspectDefinition.java src/main/java/com/aspects/smp/config/AspectsConfigParser.java src/test/java/com/aspects/smp/config/AspectsConfigParserTest.java
git commit -m "$(cat <<'EOF'
Add aspects.yml, AspectDefinition/AspectRarity, and aspect parsing

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Messages model and ConfigManager

**Files:**
- Create: `src/main/resources/messages.yml`
- Create: `src/main/java/com/aspects/smp/config/ConfigValidationException.java`
- Create: `src/main/java/com/aspects/smp/config/ConfigManager.java`

**Interfaces:**
- Consumes: `GeneralConfig`, `AspectDefinition`, `AspectsConfigParser` (Tasks 3–4).
- Produces: `ConfigManager(JavaPlugin)`; `loadAll()` (throws `ConfigValidationException`); `general() -> GeneralConfig`; `aspectDefinitions() -> Map<String, AspectDefinition>`; `message(String key) -> String`. Consumed by `AspectsPlugin` (Task 15) and every listener/command task.
- `ConfigManager` itself needs a real `JavaPlugin` (data folder, resource saving) so it is not unit-tested directly — its logic is a thin wrapper around the already-tested `AspectsConfigParser`. It is verified manually in Task 15's in-game checklist.

- [ ] **Step 1: Create `src/main/resources/messages.yml`**

```yaml
prefix: "<dark_purple>✦</dark_purple> "

first-join:
  line1: "<white>YOUR ASPECT HAS AWAKENED</white>"
  line2: "<gray>Aspect:</gray> {aspect}"
  line3: "<gray>Rarity:</gray> {rarity}"

ability:
  no-aspect: "<red>You do not have an Aspect assigned yet.</red>"
  aspect-disabled: "<red>Your Aspect is currently disabled on this server.</red>"
  world-blocked: "<red>Abilities cannot be used in this world.</red>"
  on-cooldown: "<red>Your ability is on cooldown for {seconds}s.</red>"

reroll:
  changed: "<gradient:dark_purple:aqua>Your Aspect has changed!</gradient>"
  previous: "<gray>Previous:</gray> {old}"
  new: "<gray>New Aspect:</gray> {new}"
```

- [ ] **Step 2: Create `src/main/java/com/aspects/smp/config/ConfigValidationException.java`**

```java
package com.aspects.smp.config;

import java.util.List;

public final class ConfigValidationException extends Exception {
    private final List<String> errors;

    public ConfigValidationException(List<String> errors) {
        super("Configuration validation failed: " + String.join("; ", errors));
        this.errors = errors;
    }

    public List<String> errors() {
        return errors;
    }
}
```

- [ ] **Step 3: Create `src/main/java/com/aspects/smp/config/ConfigManager.java`**

```java
package com.aspects.smp.config;

import com.aspects.smp.aspect.AspectDefinition;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConfigManager {
    private final JavaPlugin plugin;

    private GeneralConfig generalConfig;
    private Map<String, AspectDefinition> aspectDefinitions = Map.of();
    private Map<String, String> messages = Map.of();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() throws ConfigValidationException {
        plugin.saveDefaultConfig();
        saveResourceIfMissing("aspects.yml");
        saveResourceIfMissing("messages.yml");

        FileConfiguration configYml = loadYaml("config.yml");
        FileConfiguration aspectsYml = loadYaml("aspects.yml");
        FileConfiguration messagesYml = loadYaml("messages.yml");

        List<String> errors = new ArrayList<>();
        GeneralConfig stagedGeneral = AspectsConfigParser.parseGeneral(configYml, errors);
        Map<String, AspectDefinition> stagedAspects = AspectsConfigParser.parseAspects(aspectsYml, errors);

        Map<String, String> stagedMessages = new HashMap<>();
        for (Map.Entry<String, Object> entry : messagesYml.getValues(true).entrySet()) {
            if (entry.getValue() instanceof String str) {
                stagedMessages.put(entry.getKey(), str);
            }
        }

        if (!errors.isEmpty()) {
            throw new ConfigValidationException(errors);
        }

        this.generalConfig = stagedGeneral;
        this.aspectDefinitions = stagedAspects;
        this.messages = stagedMessages;
    }

    private void saveResourceIfMissing(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
    }

    private FileConfiguration loadYaml(String name) {
        File file = new File(plugin.getDataFolder(), name);
        return YamlConfiguration.loadConfiguration(file);
    }

    public GeneralConfig general() {
        return generalConfig;
    }

    public Map<String, AspectDefinition> aspectDefinitions() {
        return aspectDefinitions;
    }

    public String message(String key) {
        return messages.getOrDefault(key, key);
    }
}
```

- [ ] **Step 4: Build to verify compilation**

Run: `mvn -q package -DskipTests`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/messages.yml src/main/java/com/aspects/smp/config/ConfigValidationException.java src/main/java/com/aspects/smp/config/ConfigManager.java
git commit -m "$(cat <<'EOF'
Add messages.yml and ConfigManager tying config parsing to plugin I/O

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Data layer (SQLite persistence)

**Files:**
- Create: `src/main/java/com/aspects/smp/data/PlayerAspectData.java`
- Create: `src/main/java/com/aspects/smp/data/PlayerDataRepository.java`
- Create: `src/main/java/com/aspects/smp/data/SQLitePlayerDataRepository.java`
- Test: `src/test/java/com/aspects/smp/data/SQLitePlayerDataRepositoryTest.java`

**Interfaces:**
- Consumes: nothing new (plain JDBC, no Bukkit dependency — testable without a server).
- Produces: `PlayerAspectData(UUID playerId, String aspectId, long assignedAt)`; `PlayerDataRepository` interface with `loadPlayer(UUID) -> CompletableFuture<Optional<PlayerAspectData>>`, `savePlayer(PlayerAspectData) -> CompletableFuture<Void>`, `loadCooldowns(UUID) -> CompletableFuture<Map<String,Long>>`, `saveCooldown(UUID, String, long) -> CompletableFuture<Void>`, `close()`. Consumed by `AbilityCooldownManager` (Task 7) and `AspectManager` (Task 12).

- [ ] **Step 1: Create `src/main/java/com/aspects/smp/data/PlayerAspectData.java`**

```java
package com.aspects.smp.data;

import java.util.UUID;

public record PlayerAspectData(UUID playerId, String aspectId, long assignedAt) {
}
```

- [ ] **Step 2: Create `src/main/java/com/aspects/smp/data/PlayerDataRepository.java`**

```java
package com.aspects.smp.data;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerDataRepository {
    CompletableFuture<Optional<PlayerAspectData>> loadPlayer(UUID playerId);

    CompletableFuture<Void> savePlayer(PlayerAspectData data);

    CompletableFuture<Map<String, Long>> loadCooldowns(UUID playerId);

    CompletableFuture<Void> saveCooldown(UUID playerId, String abilityId, long expiresAtMillis);

    void close();
}
```

- [ ] **Step 3: Write the failing test**

```java
package com.aspects.smp.data;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLitePlayerDataRepositoryTest {

    private SQLitePlayerDataRepository newRepository() throws IOException {
        File dbFile = File.createTempFile("aspects-test", ".db");
        dbFile.deleteOnExit();
        return new SQLitePlayerDataRepository(dbFile, Logger.getLogger("AspectsTest"));
    }

    @Test
    void savePlayer_thenLoadPlayer_roundTrips() throws Exception {
        SQLitePlayerDataRepository repo = newRepository();
        UUID playerId = UUID.randomUUID();
        PlayerAspectData data = new PlayerAspectData(playerId, "fire", 12345L);

        repo.savePlayer(data).get();
        Optional<PlayerAspectData> loaded = repo.loadPlayer(playerId).get();

        assertTrue(loaded.isPresent());
        assertEquals("fire", loaded.get().aspectId());
        assertEquals(12345L, loaded.get().assignedAt());
        repo.close();
    }

    @Test
    void savePlayer_calledTwice_updatesInPlace() throws Exception {
        SQLitePlayerDataRepository repo = newRepository();
        UUID playerId = UUID.randomUUID();

        repo.savePlayer(new PlayerAspectData(playerId, "fire", 1L)).get();
        repo.savePlayer(new PlayerAspectData(playerId, "tide", 2L)).get();
        Optional<PlayerAspectData> loaded = repo.loadPlayer(playerId).get();

        assertTrue(loaded.isPresent());
        assertEquals("tide", loaded.get().aspectId());
        repo.close();
    }

    @Test
    void loadPlayer_unknownPlayer_returnsEmpty() throws Exception {
        SQLitePlayerDataRepository repo = newRepository();
        Optional<PlayerAspectData> loaded = repo.loadPlayer(UUID.randomUUID()).get();
        assertTrue(loaded.isEmpty());
        repo.close();
    }

    @Test
    void saveCooldown_thenLoadCooldowns_roundTrips() throws ExecutionException, InterruptedException, IOException {
        SQLitePlayerDataRepository repo = newRepository();
        UUID playerId = UUID.randomUUID();

        repo.saveCooldown(playerId, "fire", 999L).get();
        Map<String, Long> cooldowns = repo.loadCooldowns(playerId).get();

        assertEquals(1, cooldowns.size());
        assertEquals(999L, cooldowns.get("fire"));
        repo.close();
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `mvn -q test -Dtest=SQLitePlayerDataRepositoryTest`
Expected: FAIL — `SQLitePlayerDataRepository` does not exist.

- [ ] **Step 5: Create `src/main/java/com/aspects/smp/data/SQLitePlayerDataRepository.java`**

```java
package com.aspects.smp.data;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class SQLitePlayerDataRepository implements PlayerDataRepository {
    private final Connection connection;
    private final ExecutorService executor;
    private final Logger logger;

    public SQLitePlayerDataRepository(File databaseFile, Logger logger) {
        this.logger = logger;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Aspects-DB");
            thread.setDaemon(true);
            return thread;
        });
        try {
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS players (" +
                        "uuid TEXT PRIMARY KEY, aspect_id TEXT NOT NULL, assigned_at INTEGER NOT NULL)");
                statement.execute("CREATE TABLE IF NOT EXISTS cooldowns (" +
                        "uuid TEXT NOT NULL, ability_id TEXT NOT NULL, expires_at INTEGER NOT NULL, " +
                        "PRIMARY KEY (uuid, ability_id))");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to open SQLite database at " + databaseFile, e);
        }
    }

    @Override
    public CompletableFuture<Optional<PlayerAspectData>> loadPlayer(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT aspect_id, assigned_at FROM players WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.<PlayerAspectData>empty();
                    }
                    return Optional.of(new PlayerAspectData(playerId, rs.getString("aspect_id"), rs.getLong("assigned_at")));
                }
            } catch (SQLException e) {
                logger.severe("Failed to load player data for " + playerId + ": " + e.getMessage());
                return Optional.<PlayerAspectData>empty();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> savePlayer(PlayerAspectData data) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO players (uuid, aspect_id, assigned_at) VALUES (?, ?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET aspect_id = excluded.aspect_id, assigned_at = excluded.assigned_at";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, data.playerId().toString());
                ps.setString(2, data.aspectId());
                ps.setLong(3, data.assignedAt());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.severe("Failed to save player data for " + data.playerId() + ": " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Map<String, Long>> loadCooldowns(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Long> result = new HashMap<>();
            String sql = "SELECT ability_id, expires_at FROM cooldowns WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.put(rs.getString("ability_id"), rs.getLong("expires_at"));
                    }
                }
            } catch (SQLException e) {
                logger.severe("Failed to load cooldowns for " + playerId + ": " + e.getMessage());
            }
            return result;
        }, executor);
    }

    @Override
    public CompletableFuture<Void> saveCooldown(UUID playerId, String abilityId, long expiresAtMillis) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO cooldowns (uuid, ability_id, expires_at) VALUES (?, ?, ?) " +
                    "ON CONFLICT(uuid, ability_id) DO UPDATE SET expires_at = excluded.expires_at";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, playerId.toString());
                ps.setString(2, abilityId);
                ps.setLong(3, expiresAtMillis);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.severe("Failed to save cooldown for " + playerId + ": " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            connection.close();
        } catch (SQLException e) {
            logger.warning("Failed to close SQLite connection: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q test -Dtest=SQLitePlayerDataRepositoryTest`
Expected: PASS (4 tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/aspects/smp/data src/test/java/com/aspects/smp/data
git commit -m "$(cat <<'EOF'
Add SQLite-backed player data and cooldown persistence

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: AbilityCooldownManager

**Files:**
- Create: `src/main/java/com/aspects/smp/ability/AbilityCooldownManager.java`
- Create: `src/test/java/com/aspects/smp/ability/FakePlayerDataRepository.java` (test double, reused by later ability tests)
- Test: `src/test/java/com/aspects/smp/ability/AbilityCooldownManagerTest.java`

**Interfaces:**
- Consumes: `PlayerDataRepository` (Task 6).
- Produces: `AbilityCooldownManager(PlayerDataRepository)` / `AbilityCooldownManager(PlayerDataRepository, LongSupplier clock)`; `isOnCooldown(UUID, String) -> boolean`; `remainingMillis(UUID, String) -> long`; `startCooldown(UUID, String, long durationSeconds)`; `loadIntoCache(UUID, Map<String,Long>)`; `clearCache(UUID)`. Consumed by `AbilityManager` (Task 9) and `AspectManager` (Task 12).

- [ ] **Step 1: Create the shared test double `src/test/java/com/aspects/smp/ability/FakePlayerDataRepository.java`**

```java
package com.aspects.smp.ability;

import com.aspects.smp.data.PlayerAspectData;
import com.aspects.smp.data.PlayerDataRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class FakePlayerDataRepository implements PlayerDataRepository {
    public final Map<UUID, PlayerAspectData> players = new ConcurrentHashMap<>();
    public final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Optional<PlayerAspectData>> loadPlayer(UUID playerId) {
        return CompletableFuture.completedFuture(Optional.ofNullable(players.get(playerId)));
    }

    @Override
    public CompletableFuture<Void> savePlayer(PlayerAspectData data) {
        players.put(data.playerId(), data);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Map<String, Long>> loadCooldowns(UUID playerId) {
        return CompletableFuture.completedFuture(new HashMap<>(cooldowns.getOrDefault(playerId, Map.of())));
    }

    @Override
    public CompletableFuture<Void> saveCooldown(UUID playerId, String abilityId, long expiresAtMillis) {
        cooldowns.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>()).put(abilityId, expiresAtMillis);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() {
        // no-op
    }
}
```

- [ ] **Step 2: Write the failing test**

```java
package com.aspects.smp.ability;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityCooldownManagerTest {

    @Test
    void isOnCooldown_falseBeforeAnyActivation() {
        AbilityCooldownManager manager = new AbilityCooldownManager(new FakePlayerDataRepository());
        assertFalse(manager.isOnCooldown(UUID.randomUUID(), "fire"));
    }

    @Test
    void startCooldown_thenIsOnCooldown_trueUntilExpiry() {
        AtomicLong clock = new AtomicLong(0L);
        AbilityCooldownManager manager = new AbilityCooldownManager(new FakePlayerDataRepository(), clock::get);
        UUID playerId = UUID.randomUUID();

        manager.startCooldown(playerId, "fire", 10);
        assertTrue(manager.isOnCooldown(playerId, "fire"));

        clock.set(9_000L);
        assertTrue(manager.isOnCooldown(playerId, "fire"));

        clock.set(10_001L);
        assertFalse(manager.isOnCooldown(playerId, "fire"));
    }

    @Test
    void startCooldown_persistsToRepository() {
        FakePlayerDataRepository repository = new FakePlayerDataRepository();
        AbilityCooldownManager manager = new AbilityCooldownManager(repository, () -> 0L);
        UUID playerId = UUID.randomUUID();

        manager.startCooldown(playerId, "fire", 25);

        assertEquals(25_000L, repository.cooldowns.get(playerId).get("fire"));
    }

    @Test
    void loadIntoCache_seedsCooldownsFromRepository() {
        AtomicLong clock = new AtomicLong(0L);
        AbilityCooldownManager manager = new AbilityCooldownManager(new FakePlayerDataRepository(), clock::get);
        UUID playerId = UUID.randomUUID();

        manager.loadIntoCache(playerId, java.util.Map.of("fire", 5_000L));

        assertTrue(manager.isOnCooldown(playerId, "fire"));
        clock.set(5_001L);
        assertFalse(manager.isOnCooldown(playerId, "fire"));
    }

    @Test
    void clearCache_removesAllCooldownsForPlayer() {
        AbilityCooldownManager manager = new AbilityCooldownManager(new FakePlayerDataRepository(), () -> 0L);
        UUID playerId = UUID.randomUUID();
        manager.startCooldown(playerId, "fire", 100);

        manager.clearCache(playerId);

        assertFalse(manager.isOnCooldown(playerId, "fire"));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q test -Dtest=AbilityCooldownManagerTest`
Expected: FAIL — `AbilityCooldownManager` does not exist.

- [ ] **Step 4: Create `src/main/java/com/aspects/smp/ability/AbilityCooldownManager.java`**

```java
package com.aspects.smp.ability;

import com.aspects.smp.data.PlayerDataRepository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public final class AbilityCooldownManager {
    private final PlayerDataRepository repository;
    private final LongSupplier clock;
    private final Map<UUID, Map<String, Long>> expiryByPlayer = new ConcurrentHashMap<>();

    public AbilityCooldownManager(PlayerDataRepository repository) {
        this(repository, System::currentTimeMillis);
    }

    public AbilityCooldownManager(PlayerDataRepository repository, LongSupplier clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void loadIntoCache(UUID playerId, Map<String, Long> cooldowns) {
        expiryByPlayer.put(playerId, new ConcurrentHashMap<>(cooldowns));
    }

    public void clearCache(UUID playerId) {
        expiryByPlayer.remove(playerId);
    }

    public boolean isOnCooldown(UUID playerId, String abilityId) {
        return remainingMillis(playerId, abilityId) > 0;
    }

    public long remainingMillis(UUID playerId, String abilityId) {
        Map<String, Long> map = expiryByPlayer.get(playerId);
        if (map == null) {
            return 0;
        }
        Long expiry = map.get(abilityId);
        if (expiry == null) {
            return 0;
        }
        return Math.max(0, expiry - clock.getAsLong());
    }

    public void startCooldown(UUID playerId, String abilityId, long durationSeconds) {
        long expiresAt = clock.getAsLong() + durationSeconds * 1000L;
        expiryByPlayer.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>()).put(abilityId, expiresAt);
        repository.saveCooldown(playerId, abilityId, expiresAt);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q test -Dtest=AbilityCooldownManagerTest`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/aspects/smp/ability/AbilityCooldownManager.java src/test/java/com/aspects/smp/ability/FakePlayerDataRepository.java src/test/java/com/aspects/smp/ability/AbilityCooldownManagerTest.java
git commit -m "$(cat <<'EOF'
Add AbilityCooldownManager with persisted, clock-injectable cooldowns

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: Aspect runtime model, registry, and weighted assignment

**Files:**
- Create: `src/main/java/com/aspects/smp/ability/Ability.java`
- Create: `src/main/java/com/aspects/smp/ability/PassiveAbility.java`
- Create: `src/main/java/com/aspects/smp/aspect/Aspect.java`
- Create: `src/main/java/com/aspects/smp/aspect/AspectRegistry.java`
- Create: `src/main/java/com/aspects/smp/aspect/AspectAssignmentService.java`
- Test: `src/test/java/com/aspects/smp/aspect/AspectAssignmentServiceTest.java`

**Interfaces:**
- Consumes: `AspectDefinition` (Task 4). `Ability`/`PassiveAbility` are declared here because `Aspect` needs their types, but implementations come in Tasks 10–11.
- Produces: `Ability.activate(AbilityContext) -> boolean` (context type arrives in Task 9 — declared as a forward reference here, see Step 1 note); `PassiveAbility.onEquip(Player, AspectDefinition)` / `onUnequip(Player)`; `Aspect(AspectDefinition, PassiveAbility, Ability)` with `.definition()`, `.passive()`, `.active()`, `.id()`; `AspectRegistry` with `register`, `get(String) -> Optional<Aspect>`, `all() -> Collection<Aspect>`, `enabled() -> List<Aspect>`; `AspectAssignmentService.selectRandom(List<Aspect>, String excludeIdOrNull) -> Optional<Aspect>`.

Note: `Ability` references `AbilityContext`, which Task 9 defines. To avoid a forward-reference problem, this task creates `AbilityContext` as a minimal placeholder that Task 9 then fills in — both tasks touch the same file, which is fine since Task 9 runs immediately after this one.

- [ ] **Step 1: Create `src/main/java/com/aspects/smp/ability/AbilityContext.java` (minimal — extended in Task 9)**

```java
package com.aspects.smp.ability;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.Aspect;
import org.bukkit.entity.Player;

public record AbilityContext(Player player, Aspect aspect, AspectsPlugin plugin) {
}
```

- [ ] **Step 2: Create `src/main/java/com/aspects/smp/ability/Ability.java`**

```java
package com.aspects.smp.ability;

public interface Ability {
    boolean activate(AbilityContext context);
}
```

- [ ] **Step 3: Create `src/main/java/com/aspects/smp/ability/PassiveAbility.java`**

```java
package com.aspects.smp.ability;

import com.aspects.smp.aspect.AspectDefinition;
import org.bukkit.entity.Player;

public interface PassiveAbility {
    void onEquip(Player player, AspectDefinition definition);

    void onUnequip(Player player);
}
```

- [ ] **Step 4: Create `src/main/java/com/aspects/smp/aspect/Aspect.java`**

```java
package com.aspects.smp.aspect;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.PassiveAbility;

public final class Aspect {
    private final AspectDefinition definition;
    private final PassiveAbility passive;
    private final Ability active;

    public Aspect(AspectDefinition definition, PassiveAbility passive, Ability active) {
        this.definition = definition;
        this.passive = passive;
        this.active = active;
    }

    public AspectDefinition definition() {
        return definition;
    }

    public PassiveAbility passive() {
        return passive;
    }

    public Ability active() {
        return active;
    }

    public String id() {
        return definition.id();
    }
}
```

- [ ] **Step 5: Create `src/main/java/com/aspects/smp/aspect/AspectRegistry.java`**

```java
package com.aspects.smp.aspect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AspectRegistry {
    private final Map<String, Aspect> aspects = new LinkedHashMap<>();

    public void register(Aspect aspect) {
        aspects.put(aspect.id(), aspect);
    }

    public Optional<Aspect> get(String id) {
        return Optional.ofNullable(aspects.get(id));
    }

    public Collection<Aspect> all() {
        return Collections.unmodifiableCollection(aspects.values());
    }

    public List<Aspect> enabled() {
        List<Aspect> result = new ArrayList<>();
        for (Aspect aspect : aspects.values()) {
            if (aspect.definition().enabled()) {
                result.add(aspect);
            }
        }
        return result;
    }
}
```

- [ ] **Step 6: Write the failing test**

```java
package com.aspects.smp.aspect;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.PassiveAbility;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AspectAssignmentServiceTest {

    private static final PassiveAbility NO_OP_PASSIVE = new PassiveAbility() {
        @Override
        public void onEquip(Player player, AspectDefinition definition) {
        }

        @Override
        public void onUnequip(Player player) {
        }
    };
    private static final Ability NO_OP_ACTIVE = context -> true;

    private Aspect aspect(String id, boolean enabled, double weight) {
        return new Aspect(
                new AspectDefinition(id, id, "", AspectRarity.COMMON, weight, enabled, Map.of()),
                NO_OP_PASSIVE, NO_OP_ACTIVE);
    }

    @Test
    void selectRandom_excludesDisabledAspects() {
        List<Aspect> pool = List.of(aspect("fire", false, 10), aspect("tide", true, 10));
        AspectAssignmentService service = new AspectAssignmentService(() -> 0.0);

        Optional<Aspect> picked = service.selectRandom(pool, null);

        assertTrue(picked.isPresent());
        assertEquals("tide", picked.get().id());
    }

    @Test
    void selectRandom_excludesZeroWeightAspects() {
        List<Aspect> pool = List.of(aspect("fire", true, 0), aspect("tide", true, 10));
        AspectAssignmentService service = new AspectAssignmentService(() -> 0.0);

        Optional<Aspect> picked = service.selectRandom(pool, null);

        assertTrue(picked.isPresent());
        assertEquals("tide", picked.get().id());
    }

    @Test
    void selectRandom_excludesCurrentAspectWhenAnotherIsEligible() {
        List<Aspect> pool = List.of(aspect("fire", true, 10), aspect("tide", true, 10));
        AspectAssignmentService service = new AspectAssignmentService(() -> 0.0);

        Optional<Aspect> picked = service.selectRandom(pool, "fire");

        assertTrue(picked.isPresent());
        assertEquals("tide", picked.get().id());
    }

    @Test
    void selectRandom_exclusionYieldsToSoleEligibleAspect() {
        List<Aspect> pool = List.of(aspect("fire", true, 10));
        AspectAssignmentService service = new AspectAssignmentService(() -> 0.0);

        Optional<Aspect> picked = service.selectRandom(pool, "fire");

        assertTrue(picked.isPresent());
        assertEquals("fire", picked.get().id());
    }

    @Test
    void selectRandom_noEligibleAspects_returnsEmpty() {
        List<Aspect> pool = List.of(aspect("fire", false, 10));
        AspectAssignmentService service = new AspectAssignmentService(() -> 0.0);

        assertTrue(service.selectRandom(pool, null).isEmpty());
    }

    @Test
    void selectRandom_respectsWeightProportions() {
        List<Aspect> pool = List.of(aspect("common", true, 80), aspect("rare", true, 20));
        AtomicInteger commonCount = new AtomicInteger();
        AtomicInteger rareCount = new AtomicInteger();
        java.util.Random random = new java.util.Random(42);
        AspectAssignmentService service = new AspectAssignmentService(random::nextDouble);

        for (int i = 0; i < 10_000; i++) {
            Aspect picked = service.selectRandom(pool, null).orElseThrow();
            if (picked.id().equals("common")) {
                commonCount.incrementAndGet();
            } else {
                rareCount.incrementAndGet();
            }
        }

        double commonRatio = commonCount.get() / 10_000.0;
        assertTrue(commonRatio > 0.75 && commonRatio < 0.85,
                "expected ~80% common picks, got " + commonRatio);
    }
}
```

- [ ] **Step 7: Run test to verify it fails**

Run: `mvn -q test -Dtest=AspectAssignmentServiceTest`
Expected: FAIL — `AspectAssignmentService` does not exist.

- [ ] **Step 8: Create `src/main/java/com/aspects/smp/aspect/AspectAssignmentService.java`**

```java
package com.aspects.smp.aspect;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleSupplier;

public final class AspectAssignmentService {
    private final DoubleSupplier randomSource;

    public AspectAssignmentService() {
        this(Math::random);
    }

    public AspectAssignmentService(DoubleSupplier randomSource) {
        this.randomSource = randomSource;
    }

    public Optional<Aspect> selectRandom(List<Aspect> pool, String excludeId) {
        List<Aspect> eligible = new ArrayList<>();
        for (Aspect aspect : pool) {
            if (!aspect.definition().enabled()) {
                continue;
            }
            if (aspect.definition().weight() <= 0) {
                continue;
            }
            eligible.add(aspect);
        }

        if (eligible.size() > 1 && excludeId != null) {
            List<Aspect> withoutCurrent = new ArrayList<>();
            for (Aspect aspect : eligible) {
                if (!aspect.id().equals(excludeId)) {
                    withoutCurrent.add(aspect);
                }
            }
            if (!withoutCurrent.isEmpty()) {
                eligible = withoutCurrent;
            }
        }

        if (eligible.isEmpty()) {
            return Optional.empty();
        }

        double totalWeight = 0;
        for (Aspect aspect : eligible) {
            totalWeight += aspect.definition().weight();
        }

        double roll = randomSource.getAsDouble() * totalWeight;
        double cumulative = 0;
        for (Aspect aspect : eligible) {
            cumulative += aspect.definition().weight();
            if (roll < cumulative) {
                return Optional.of(aspect);
            }
        }
        return Optional.of(eligible.get(eligible.size() - 1));
    }
}
```

- [ ] **Step 9: Run test to verify it passes**

Run: `mvn -q test -Dtest=AspectAssignmentServiceTest`
Expected: PASS (6 tests).

- [ ] **Step 10: Full build check**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/aspects/smp/ability/Ability.java src/main/java/com/aspects/smp/ability/PassiveAbility.java src/main/java/com/aspects/smp/ability/AbilityContext.java src/main/java/com/aspects/smp/aspect/Aspect.java src/main/java/com/aspects/smp/aspect/AspectRegistry.java src/main/java/com/aspects/smp/aspect/AspectAssignmentService.java src/test/java/com/aspects/smp/aspect/AspectAssignmentServiceTest.java
git commit -m "$(cat <<'EOF'
Add Aspect/Ability model, registry, and weighted random assignment

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 9: ProtectionGate and AbilityManager orchestration

**Files:**
- Create: `src/main/java/com/aspects/smp/ability/ProtectionGate.java`
- Create: `src/main/java/com/aspects/smp/ability/AbilityManager.java`

**Interfaces:**
- Consumes: `Aspect`/`AspectRegistry` (Task 8), `AbilityCooldownManager` (Task 7), `AspectManager` (forward reference — full implementation arrives in Task 12; this task only calls `aspectManager.getAspect(UUID)`, which is declared now and implemented in Task 12).
- Produces: `ProtectionGate(Collection<String> blockedWorlds)` with `isAllowed(Player) -> boolean`; `AbilityManager(AspectManager, AbilityCooldownManager, ProtectionGate, AspectsPlugin)` with `tryActivate(Player) -> ActivationResult`, and nested `record ActivationResult(boolean success, String failureMessage)` with static factories `succeeded()` / `failed(String)`. (Named `succeeded`/`failed`, not `success`/`failure`, because a static zero-arg method named `success()` would collide with the record's own auto-generated `success()` accessor — a real compile error, not a style choice.) `ActivationListener` (Task 13) only ever calls the instance accessors `result.success()` / `result.failureMessage()`, so it is unaffected by the static factories' names. Consumed by `ActivationListener` (Task 13) and `AspectsPlugin` (Task 15).
- This task also requires a minimal `AspectManager` stub (just the `getAspect` method used here) so the project compiles before Task 12 fleshes it out fully — see Step 1.

**Note on testing:** `AbilityManager.tryActivate` requires a live `Player` (gamemode, death state, world) that cannot be constructed without a running Bukkit server, so it is not unit-tested in this phase. It is exercised by the in-game manual verification checklist in Task 15. Its dependencies (`AbilityCooldownManager`, `AspectAssignmentService`) already have solid unit coverage from Tasks 7–8.

- [ ] **Step 1: Create a minimal `src/main/java/com/aspects/smp/aspect/AspectManager.java` stub (fully implemented in Task 12)**

```java
package com.aspects.smp.aspect;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class AspectManager {
    private final Map<UUID, Aspect> assigned = new ConcurrentHashMap<>();

    public Optional<Aspect> getAspect(UUID playerId) {
        return Optional.ofNullable(assigned.get(playerId));
    }
}
```

- [ ] **Step 2: Create `src/main/java/com/aspects/smp/ability/ProtectionGate.java`**

```java
package com.aspects.smp.ability;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class ProtectionGate {
    private final Set<String> blockedWorlds;

    public ProtectionGate(Collection<String> blockedWorlds) {
        this.blockedWorlds = new HashSet<>(blockedWorlds);
    }

    public boolean isAllowed(Player player) {
        return !blockedWorlds.contains(player.getWorld().getName());
    }
}
```

- [ ] **Step 3: Create `src/main/java/com/aspects/smp/ability/AbilityManager.java`**

```java
package com.aspects.smp.ability;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.Aspect;
import com.aspects.smp.aspect.AspectManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public final class AbilityManager {
    private final AspectManager aspectManager;
    private final AbilityCooldownManager cooldownManager;
    private final ProtectionGate protectionGate;
    private final AspectsPlugin plugin;

    public AbilityManager(AspectManager aspectManager, AbilityCooldownManager cooldownManager,
                           ProtectionGate protectionGate, AspectsPlugin plugin) {
        this.aspectManager = aspectManager;
        this.cooldownManager = cooldownManager;
        this.protectionGate = protectionGate;
        this.plugin = plugin;
    }

    public ActivationResult tryActivate(Player player) {
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            return ActivationResult.failed("You cannot use abilities right now.");
        }

        Aspect aspect = aspectManager.getAspect(player.getUniqueId()).orElse(null);
        if (aspect == null) {
            return ActivationResult.failed("You do not have an Aspect assigned yet.");
        }
        if (!aspect.definition().enabled()) {
            return ActivationResult.failed("Your Aspect is currently disabled on this server.");
        }
        if (!protectionGate.isAllowed(player)) {
            return ActivationResult.failed("Abilities cannot be used in this world.");
        }
        if (cooldownManager.isOnCooldown(player.getUniqueId(), aspect.id())) {
            long seconds = (cooldownManager.remainingMillis(player.getUniqueId(), aspect.id()) + 999) / 1000;
            return ActivationResult.failed("Your ability is on cooldown for " + seconds + "s.");
        }

        AbilityContext context = new AbilityContext(player, aspect, plugin);
        boolean activated = aspect.active().activate(context);
        if (!activated) {
            return ActivationResult.failed("Your ability could not be activated right now.");
        }

        long cooldownSeconds = (long) aspect.definition().getDouble("active.cooldown-seconds", 20.0);
        cooldownManager.startCooldown(player.getUniqueId(), aspect.id(), cooldownSeconds);
        return ActivationResult.succeeded();
    }

    public record ActivationResult(boolean success, String failureMessage) {
        public static ActivationResult succeeded() {
            return new ActivationResult(true, null);
        }

        public static ActivationResult failed(String message) {
            return new ActivationResult(false, message);
        }
    }
}
```

- [ ] **Step 4: Build to verify compilation**

Run: `mvn -q package -DskipTests`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/aspects/smp/ability/ProtectionGate.java src/main/java/com/aspects/smp/ability/AbilityManager.java src/main/java/com/aspects/smp/aspect/AspectManager.java
git commit -m "$(cat <<'EOF'
Add ProtectionGate and AbilityManager activation orchestration

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 10: SoundUtil, ParticleUtil, and the Fire Aspect

**Files:**
- Create: `src/main/java/com/aspects/smp/util/SoundUtil.java`
- Create: `src/main/java/com/aspects/smp/util/ParticleUtil.java`
- Create: `src/main/java/com/aspects/smp/ability/impl/FirePassiveAbility.java`
- Create: `src/main/java/com/aspects/smp/ability/impl/FireActiveAbility.java`

**Interfaces:**
- Consumes: `AbilityContext`, `PassiveAbility`, `Ability` (Task 8/9); `AspectDefinition.getDouble/getInt/getBoolean` (Task 4).
- Produces: `SoundUtil.play(Player, String soundName, float volume, float pitch, Logger)`; `ParticleUtil.spawn(Location, String particleName, int count, Logger)` — reused by Task 11's Tide implementation and Task 13's first-join listener; `FirePassiveAbility`, `FireActiveAbility`.
- **Manual verification only** (documented in Task 15's checklist): this ability mutates live entities/particles and cannot be meaningfully unit-tested without a running server.

- [ ] **Step 1: Create `src/main/java/com/aspects/smp/util/SoundUtil.java`**

```java
package com.aspects.smp.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

public final class SoundUtil {
    private SoundUtil() {}

    public static void play(Player player, String soundName, float volume, float pitch, Logger logger) {
        if (soundName == null || soundName.isBlank()) {
            return;
        }
        NamespacedKey key = NamespacedKey.minecraft(soundName.toLowerCase());
        Sound sound = Registry.SOUNDS.get(key);
        if (sound == null) {
            logger.warning("Unknown sound '" + soundName + "' in configuration, skipping.");
            return;
        }
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}
```

- [ ] **Step 2: Create `src/main/java/com/aspects/smp/util/ParticleUtil.java`**

```java
package com.aspects.smp.util;

import org.bukkit.Location;
import org.bukkit.Particle;

import java.util.logging.Logger;

public final class ParticleUtil {
    private ParticleUtil() {}

    public static void spawn(Location location, String particleName, int count, Logger logger) {
        if (particleName == null || particleName.isBlank()) {
            return;
        }
        Particle particle;
        try {
            particle = Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown particle '" + particleName + "' in configuration, skipping.");
            return;
        }
        location.getWorld().spawnParticle(particle, location, count, 0.5, 0.5, 0.5, 0.01);
    }
}
```

- [ ] **Step 3: Create `src/main/java/com/aspects/smp/ability/impl/FirePassiveAbility.java`**

```java
package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.PassiveAbility;
import com.aspects.smp.aspect.AspectDefinition;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class FirePassiveAbility implements PassiveAbility {
    @Override
    public void onEquip(Player player, AspectDefinition definition) {
        if (!definition.getBoolean("passive.fire-resistance", true)) {
            return;
        }
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, true, false, false));
    }

    @Override
    public void onUnequip(Player player) {
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
    }
}
```

- [ ] **Step 4: Create `src/main/java/com/aspects/smp/ability/impl/FireActiveAbility.java`**

```java
package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.AbilityContext;
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.util.ParticleUtil;
import com.aspects.smp.util.SoundUtil;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class FireActiveAbility implements Ability {
    @Override
    public boolean activate(AbilityContext context) {
        Player caster = context.player();
        AspectDefinition definition = context.aspect().definition();

        double radius = definition.getDouble("active.radius", 4.0);
        double damage = definition.getDouble("active.damage", 4.0);
        int burnTicks = definition.getInt("active.burn-ticks", 60);
        int windupTicks = definition.getInt("active.windup-ticks", 10);

        SoundUtil.play(caster, "entity.blaze.shoot", 1.0f, 1.0f, context.plugin().getLogger());
        ParticleUtil.spawn(caster.getLocation(), "FLAME", 15, context.plugin().getLogger());

        Location origin = caster.getLocation();
        context.plugin().getServer().getScheduler().runTaskLater(context.plugin(), () -> {
            if (!caster.isOnline()) {
                return;
            }
            List<Entity> nearby = new ArrayList<>(caster.getWorld().getNearbyEntities(origin, radius, radius, radius));
            for (Entity entity : nearby) {
                if (!(entity instanceof Player target) || target.getUniqueId().equals(caster.getUniqueId())) {
                    continue;
                }
                if (target.getLocation().distance(origin) > radius) {
                    continue;
                }
                target.damage(damage, caster);
                target.setFireTicks(Math.max(target.getFireTicks(), burnTicks));
            }
            SoundUtil.play(caster, "entity.generic.explode", 1.0f, 1.0f, context.plugin().getLogger());
            ParticleUtil.spawn(origin, "LAVA", 25, context.plugin().getLogger());
        }, windupTicks);

        return true;
    }
}
```

- [ ] **Step 5: Build to verify compilation**

Run: `mvn -q package -DskipTests`
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/aspects/smp/util/SoundUtil.java src/main/java/com/aspects/smp/util/ParticleUtil.java src/main/java/com/aspects/smp/ability/impl/FirePassiveAbility.java src/main/java/com/aspects/smp/ability/impl/FireActiveAbility.java
git commit -m "$(cat <<'EOF'
Implement the Fire Aspect (fire resistance passive, Inferno Burst active)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 11: Tide Aspect

**Files:**
- Create: `src/main/java/com/aspects/smp/ability/impl/TidePassiveAbility.java`
- Create: `src/main/java/com/aspects/smp/ability/impl/TideActiveAbility.java`

**Interfaces:**
- Consumes: `SoundUtil`, `ParticleUtil` (Task 10); `AbilityContext`, `PassiveAbility`, `Ability` (Task 8/9).
- Produces: `TidePassiveAbility`, `TideActiveAbility`.
- **Manual verification only**, same reasoning as Task 10.

- [ ] **Step 1: Create `src/main/java/com/aspects/smp/ability/impl/TidePassiveAbility.java`**

```java
package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.PassiveAbility;
import com.aspects.smp.aspect.AspectDefinition;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class TidePassiveAbility implements PassiveAbility {
    @Override
    public void onEquip(Player player, AspectDefinition definition) {
        if (!definition.getBoolean("passive.water-breathing", true)) {
            return;
        }
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.WATER_BREATHING, PotionEffect.INFINITE_DURATION, 0, true, false, false));
    }

    @Override
    public void onUnequip(Player player) {
        player.removePotionEffect(PotionEffectType.WATER_BREATHING);
    }
}
```

- [ ] **Step 2: Create `src/main/java/com/aspects/smp/ability/impl/TideActiveAbility.java`**

```java
package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.AbilityContext;
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.util.ParticleUtil;
import com.aspects.smp.util.SoundUtil;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

public final class TideActiveAbility implements Ability {
    @Override
    public boolean activate(AbilityContext context) {
        Player player = context.player();
        AspectDefinition definition = context.aspect().definition();
        double healAmount = definition.getDouble("active.heal-amount", 4.0);

        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        double newHealth = Math.min(maxHealth, player.getHealth() + healAmount);
        player.setHealth(newHealth);

        SoundUtil.play(player, "entity.generic.drink", 1.0f, 1.0f, context.plugin().getLogger());
        ParticleUtil.spawn(player.getLocation(), "DRIPPING_WATER", 20, context.plugin().getLogger());
        return true;
    }
}
```

- [ ] **Step 3: Build to verify compilation**

Run: `mvn -q package -DskipTests`
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/aspects/smp/ability/impl/TidePassiveAbility.java src/main/java/com/aspects/smp/ability/impl/TideActiveAbility.java
git commit -m "$(cat <<'EOF'
Implement the Tide Aspect (water breathing passive, Healing Wave active)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 12: AspectManager facade (full implementation)

**Files:**
- Modify: `src/main/java/com/aspects/smp/aspect/AspectManager.java` (replace the Task 9 stub)

**Interfaces:**
- Consumes: `AspectRegistry`, `AspectAssignmentService` (Task 8); `PlayerDataRepository` (Task 6); `AbilityCooldownManager` (Task 7).
- Produces: `AspectManager(AspectRegistry, AspectAssignmentService, PlayerDataRepository, AbilityCooldownManager, Logger, boolean excludeCurrentOnReroll, Executor mainThreadExecutor)`; `getAspect(UUID) -> Optional<Aspect>` (already relied on by Task 9); `loadOrAssign(Player, Runnable onFirstJoin)`; `reapplyPassive(Player)`; `unload(Player)`; `adminSet(Player, String) -> Optional<Aspect>`; `adminReroll(Player) -> Optional<Aspect>`. Consumed by `AspectsPlugin`, listeners, and commands (Tasks 13–15).
- **Manual verification only**: this class's interesting behavior (loading from a real repository, dispatching to the main thread, mutating a live `Player`) requires a running server; `AspectAssignmentService` and `AbilityCooldownManager`, which it composes, are already unit-tested.

- [ ] **Step 1: Replace `src/main/java/com/aspects/smp/aspect/AspectManager.java` with the full implementation**

```java
package com.aspects.smp.aspect;

import com.aspects.smp.ability.AbilityCooldownManager;
import com.aspects.smp.data.PlayerAspectData;
import com.aspects.smp.data.PlayerDataRepository;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

public final class AspectManager {
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

    public Optional<Aspect> getAspect(UUID playerId) {
        return Optional.ofNullable(assigned.get(playerId));
    }

    public void loadOrAssign(Player player, Runnable onFirstJoin) {
        UUID id = player.getUniqueId();
        repository.loadPlayer(id).thenAccept(existing -> mainThreadExecutor.execute(() -> {
            if (existing.isPresent()) {
                Aspect aspect = registry.get(existing.get().aspectId()).orElse(null);
                if (aspect == null || !aspect.definition().enabled()) {
                    logger.warning("Player " + player.getName() + " had an invalid or disabled aspect '" +
                            existing.get().aspectId() + "'; reassigning.");
                    assignNew(player, null, onFirstJoin);
                    return;
                }
                assigned.put(id, aspect);
                aspect.passive().onEquip(player, aspect.definition());
                repository.loadCooldowns(id).thenAccept(cooldowns -> cooldownManager.loadIntoCache(id, cooldowns));
            } else {
                assignNew(player, null, onFirstJoin);
            }
        }));
    }

    private void assignNew(Player player, String excludeId, Runnable onAssigned) {
        Optional<Aspect> picked = assignmentService.selectRandom(new ArrayList<>(registry.enabled()), excludeId);
        if (picked.isEmpty()) {
            logger.severe("No eligible Aspect could be assigned to " + player.getName() +
                    " — check that at least one Aspect is enabled with a positive weight.");
            return;
        }
        Aspect aspect = picked.get();
        assigned.put(player.getUniqueId(), aspect);
        repository.savePlayer(new PlayerAspectData(player.getUniqueId(), aspect.id(), System.currentTimeMillis()));
        aspect.passive().onEquip(player, aspect.definition());
        if (onAssigned != null) {
            onAssigned.run();
        }
    }

    public void reapplyPassive(Player player) {
        Aspect aspect = assigned.get(player.getUniqueId());
        if (aspect != null) {
            aspect.passive().onEquip(player, aspect.definition());
        }
    }

    public void unload(Player player) {
        Aspect aspect = assigned.remove(player.getUniqueId());
        if (aspect != null) {
            aspect.passive().onUnequip(player);
        }
        cooldownManager.clearCache(player.getUniqueId());
    }

    public Optional<Aspect> adminSet(Player player, String aspectId) {
        Aspect aspect = registry.get(aspectId).orElse(null);
        if (aspect == null || !aspect.definition().enabled()) {
            return Optional.empty();
        }
        Aspect previous = assigned.get(player.getUniqueId());
        if (previous != null) {
            previous.passive().onUnequip(player);
        }
        assigned.put(player.getUniqueId(), aspect);
        repository.savePlayer(new PlayerAspectData(player.getUniqueId(), aspect.id(), System.currentTimeMillis()));
        cooldownManager.clearCache(player.getUniqueId());
        aspect.passive().onEquip(player, aspect.definition());
        return Optional.of(aspect);
    }

    public Optional<Aspect> adminReroll(Player player) {
        Aspect previous = assigned.get(player.getUniqueId());
        String excludeId = excludeCurrentOnReroll && previous != null ? previous.id() : null;
        Optional<Aspect> picked = assignmentService.selectRandom(new ArrayList<>(registry.enabled()), excludeId);
        if (picked.isEmpty()) {
            return Optional.empty();
        }
        if (previous != null) {
            previous.passive().onUnequip(player);
        }
        Aspect aspect = picked.get();
        assigned.put(player.getUniqueId(), aspect);
        repository.savePlayer(new PlayerAspectData(player.getUniqueId(), aspect.id(), System.currentTimeMillis()));
        cooldownManager.clearCache(player.getUniqueId());
        aspect.passive().onEquip(player, aspect.definition());
        return Optional.of(aspect);
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `mvn -q package -DskipTests`
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/aspects/smp/aspect/AspectManager.java
git commit -m "$(cat <<'EOF'
Implement AspectManager: load/assign, admin set/reroll, passive lifecycle

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 13: Listeners

**Files:**
- Create: `src/main/java/com/aspects/smp/listener/PlayerJoinListener.java`
- Create: `src/main/java/com/aspects/smp/listener/PlayerRespawnListener.java`
- Create: `src/main/java/com/aspects/smp/listener/PlayerQuitListener.java`
- Create: `src/main/java/com/aspects/smp/ability/ActivationListener.java`

**Interfaces:**
- Consumes: `AspectsPlugin.getAspectManager()` / `.getConfigManager()` (declared here, fully wired in Task 15); `AbilityManager.tryActivate` (Task 9); `MessageUtil`, `SoundUtil`, `ParticleUtil` (Tasks 2, 10).
- Produces: `PlayerJoinListener(AspectsPlugin)`, `PlayerRespawnListener(AspectsPlugin)`, `PlayerQuitListener(AspectsPlugin)`, `ActivationListener(AbilityManager, JavaPlugin)` — all `Listener`s registered by `AspectsPlugin` in Task 15.
- No `PlayerDeathListener` is created in this phase: neither Fire's nor Tide's abilities leave any lingering state on the caster that death needs to clean up (Fire only affects nearby enemies; Tide's heal is instant), and "dying does not reroll the Aspect" is satisfied simply by there being no reroll-on-death code anywhere. A death listener will become necessary once a later phase adds an aspect with a caster-side timed buff (e.g. Blood Pact) — add it then, not speculatively now.
- This task requires `AspectsPlugin` to expose `getAspectManager()` and `getConfigManager()`. Those accessors are added as part of Task 15's full wiring; until then this code will not compile standalone. Task 15 immediately follows this one and completes the wiring, so the project is only briefly non-compiling between these two tasks (acceptable per the plan's sequencing — commit normally, Task 15 brings the build back green).
- **Manual verification only**: event-driven, needs a running server.

- [ ] **Step 1: Create `src/main/java/com/aspects/smp/listener/PlayerJoinListener.java`**

```java
package com.aspects.smp.listener;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.Aspect;
import com.aspects.smp.config.GeneralConfig;
import com.aspects.smp.util.MessageUtil;
import com.aspects.smp.util.ParticleUtil;
import com.aspects.smp.util.SoundUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Map;

public final class PlayerJoinListener implements Listener {
    private final AspectsPlugin plugin;

    public PlayerJoinListener(AspectsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getAspectManager().loadOrAssign(player, () -> announceFirstJoin(player));
    }

    private void announceFirstJoin(Player player) {
        Aspect aspect = plugin.getAspectManager().getAspect(player.getUniqueId()).orElse(null);
        if (aspect == null) {
            return;
        }
        GeneralConfig general = plugin.getConfigManager().general();
        if (!general.firstJoinEnabled()) {
            return;
        }

        MessageUtil.sendMessage(player, plugin.getConfigManager().message("first-join.line1"));
        MessageUtil.sendMessage(player, plugin.getConfigManager().message("first-join.line2"),
                Map.of("aspect", aspect.definition().displayName()));
        MessageUtil.sendMessage(player, plugin.getConfigManager().message("first-join.line3"),
                Map.of("rarity", aspect.definition().rarity().name()));
        SoundUtil.play(player, general.firstJoinSound(), 1.0f, 1.0f, plugin.getLogger());
        ParticleUtil.spawn(player.getLocation(), general.firstJoinParticle(),
                general.firstJoinParticleCount(), plugin.getLogger());
    }
}
```

- [ ] **Step 2: Create `src/main/java/com/aspects/smp/listener/PlayerRespawnListener.java`**

```java
package com.aspects.smp.listener;

import com.aspects.smp.AspectsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class PlayerRespawnListener implements Listener {
    private final AspectsPlugin plugin;

    public PlayerRespawnListener(AspectsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTask(plugin,
                () -> plugin.getAspectManager().reapplyPassive(event.getPlayer()));
    }
}
```

- [ ] **Step 3: Create `src/main/java/com/aspects/smp/listener/PlayerQuitListener.java`**

```java
package com.aspects.smp.listener;

import com.aspects.smp.AspectsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerQuitListener implements Listener {
    private final AspectsPlugin plugin;

    public PlayerQuitListener(AspectsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getAspectManager().unload(event.getPlayer());
    }
}
```

- [ ] **Step 4: Create `src/main/java/com/aspects/smp/ability/ActivationListener.java`**

```java
package com.aspects.smp.ability;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ActivationListener implements Listener {
    private final AbilityManager abilityManager;
    private final JavaPlugin plugin;
    private final Set<UUID> debounce = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public ActivationListener(AbilityManager abilityManager, JavaPlugin plugin) {
        this.abilityManager = abilityManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        if (player.getInventory().getItemInMainHand().getType() != Material.AIR) {
            return;
        }
        if (!debounce.add(player.getUniqueId())) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> debounce.remove(player.getUniqueId()));

        AbilityManager.ActivationResult result = abilityManager.tryActivate(player);
        if (!result.success()) {
            player.sendActionBar(Component.text(result.failureMessage()));
        }
    }
}
```

- [ ] **Step 5: Commit (build will fail until Task 15 — see note above; commit anyway to keep history granular)**

```bash
git add src/main/java/com/aspects/smp/listener src/main/java/com/aspects/smp/ability/ActivationListener.java
git commit -m "$(cat <<'EOF'
Add join/respawn/quit listeners and the sneak+empty-hand activation trigger

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 14: Commands

**Files:**
- Create: `src/main/java/com/aspects/smp/command/AspectsCommand.java`
- Create: `src/main/java/com/aspects/smp/command/AdminCommand.java`
- Create: `src/main/java/com/aspects/smp/command/AspectsTabCompleter.java`

**Interfaces:**
- Consumes: `AspectsPlugin.getAspectManager()` / `.getConfigManager()` (Task 15 wiring, same note as Task 13); `MessageUtil` (Task 2).
- Produces: `AspectsCommand(AspectsPlugin, AdminCommand)` implementing `CommandExecutor`; `AdminCommand(AspectsPlugin)` with `handle(CommandSender, String[]) -> boolean`; `AspectsTabCompleter(AspectsPlugin)` implementing `TabCompleter`. Wired into `plugin.yml`'s `aspects` command by `AspectsPlugin` in Task 15.
- **Manual verification only**: command execution needs a running server / console.

- [ ] **Step 1: Create `src/main/java/com/aspects/smp/command/AdminCommand.java`**

```java
package com.aspects.smp.command;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.Aspect;
import com.aspects.smp.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;

public final class AdminCommand {
    private final AspectsPlugin plugin;

    public AdminCommand(AspectsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(MessageUtil.parse("<red>Usage: /aspects admin <set|reroll> <player> [aspect]</red>"));
            return true;
        }
        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "set" -> handleSet(sender, args);
            case "reroll" -> handleReroll(sender, args);
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
}
```

- [ ] **Step 2: Create `src/main/java/com/aspects/smp/command/AspectsCommand.java`**

```java
package com.aspects.smp.command;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.Aspect;
import com.aspects.smp.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AspectsCommand implements CommandExecutor {
    private final AspectsPlugin plugin;
    private final AdminCommand adminCommand;

    public AspectsCommand(AspectsPlugin plugin, AdminCommand adminCommand) {
        this.plugin = plugin;
        this.adminCommand = adminCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            return adminCommand.handle(sender, shift(args));
        }
        return handleInfo(sender);
    }

    private boolean handleInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>Only players have an Aspect. Use /aspects admin debug <player> from console.</red>"));
            return true;
        }
        if (!player.hasPermission("aspects.use")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to use this command.</red>"));
            return true;
        }
        Aspect aspect = plugin.getAspectManager().getAspect(player.getUniqueId()).orElse(null);
        if (aspect == null) {
            MessageUtil.sendMessage(player, plugin.getConfigManager().message("ability.no-aspect"));
            return true;
        }
        player.sendMessage(MessageUtil.parse("<dark_purple>Aspect:</dark_purple> " + aspect.definition().displayName()));
        player.sendMessage(MessageUtil.parse(
                "<dark_purple>Rarity:</dark_purple> <white>" + aspect.definition().rarity().name() + "</white>"));
        player.sendMessage(MessageUtil.parse(
                "<dark_purple>Description:</dark_purple> <white>" + aspect.definition().description() + "</white>"));
        return true;
    }

    private String[] shift(String[] args) {
        String[] result = new String[args.length - 1];
        System.arraycopy(args, 1, result, 0, result.length);
        return result;
    }
}
```

- [ ] **Step 3: Create `src/main/java/com/aspects/smp/command/AspectsTabCompleter.java`**

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
            options.add("admin");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            options.add("set");
            options.add("reroll");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin")
                && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("reroll"))) {
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

- [ ] **Step 4: Commit (build still fails until Task 15 wires `AspectsPlugin`'s accessors)**

```bash
git add src/main/java/com/aspects/smp/command
git commit -m "$(cat <<'EOF'
Add /aspects info and /aspects admin set|reroll commands with tab completion

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 15: Final wiring, full build, and manual verification

**Files:**
- Modify: `src/main/java/com/aspects/smp/AspectsPlugin.java` (replace the Task 1 skeleton with full wiring)

**Interfaces:**
- Consumes: everything from Tasks 2–14.
- Produces: `AspectsPlugin.getConfigManager() -> ConfigManager`, `AspectsPlugin.getAspectManager() -> AspectManager` — satisfies the forward references from Tasks 13–14.

- [ ] **Step 1: Replace `src/main/java/com/aspects/smp/AspectsPlugin.java` with the full implementation**

```java
package com.aspects.smp;

import com.aspects.smp.ability.AbilityCooldownManager;
import com.aspects.smp.ability.AbilityManager;
import com.aspects.smp.ability.ActivationListener;
import com.aspects.smp.ability.ProtectionGate;
import com.aspects.smp.ability.impl.FireActiveAbility;
import com.aspects.smp.ability.impl.FirePassiveAbility;
import com.aspects.smp.ability.impl.TideActiveAbility;
import com.aspects.smp.ability.impl.TidePassiveAbility;
import com.aspects.smp.aspect.Aspect;
import com.aspects.smp.aspect.AspectAssignmentService;
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.aspect.AspectManager;
import com.aspects.smp.aspect.AspectRegistry;
import com.aspects.smp.command.AdminCommand;
import com.aspects.smp.command.AspectsCommand;
import com.aspects.smp.command.AspectsTabCompleter;
import com.aspects.smp.config.ConfigManager;
import com.aspects.smp.config.ConfigValidationException;
import com.aspects.smp.data.PlayerDataRepository;
import com.aspects.smp.data.SQLitePlayerDataRepository;
import com.aspects.smp.listener.PlayerJoinListener;
import com.aspects.smp.listener.PlayerQuitListener;
import com.aspects.smp.listener.PlayerRespawnListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;

public final class AspectsPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private PlayerDataRepository dataRepository;
    private AspectRegistry aspectRegistry;
    private AspectManager aspectManager;
    private AbilityCooldownManager cooldownManager;
    private AbilityManager abilityManager;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        try {
            configManager.loadAll();
        } catch (ConfigValidationException e) {
            getLogger().severe("Failed to load configuration, disabling plugin:");
            e.errors().forEach(error -> getLogger().severe(" - " + error));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        dataRepository = new SQLitePlayerDataRepository(
                new File(getDataFolder(), configManager.general().sqliteFile()), getLogger());

        aspectRegistry = buildRegistry(configManager.aspectDefinitions());

        cooldownManager = new AbilityCooldownManager(dataRepository);
        aspectManager = new AspectManager(aspectRegistry, new AspectAssignmentService(), dataRepository,
                cooldownManager, getLogger(), configManager.general().excludeCurrentOnReroll(),
                runnable -> getServer().getScheduler().runTask(this, runnable));

        ProtectionGate protectionGate = new ProtectionGate(configManager.general().blockedWorlds());
        abilityManager = new AbilityManager(aspectManager, cooldownManager, protectionGate, this);

        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new ActivationListener(abilityManager, this), this);

        AdminCommand adminCommand = new AdminCommand(this);
        AspectsCommand aspectsCommand = new AspectsCommand(this, adminCommand);
        AspectsTabCompleter tabCompleter = new AspectsTabCompleter(this);
        PluginCommand command = getCommand("aspects");
        if (command != null) {
            command.setExecutor(aspectsCommand);
            command.setTabCompleter(tabCompleter);
        }

        getLogger().info("Aspects enabled with " + aspectRegistry.enabled().size() + " enabled aspect(s).");
    }

    @Override
    public void onDisable() {
        if (dataRepository != null) {
            dataRepository.close();
        }
    }

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

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public AspectManager getAspectManager() {
        return aspectManager;
    }
}
```

- [ ] **Step 2: Full build and test run**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`, all unit tests from Tasks 2–8 pass, and `target/aspects-1.0.0-SNAPSHOT.jar` exists.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/aspects/smp/AspectsPlugin.java
git commit -m "$(cat <<'EOF'
Wire config, data, aspect, ability, listener, and command layers together

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 4: Manual in-game verification checklist**

This step requires a running Paper 1.21.11 server with the built jar dropped into its `plugins/` folder. None of this is covered by the automated tests above — it must be performed by hand and reported honestly as "server-verified" only after actually doing it:

1. Start the server, confirm `plugins/Aspects/config.yml`, `aspects.yml`, `messages.yml` are generated and no config validation errors are logged.
2. Join as a brand-new player (or one with no prior Aspects data): confirm a first-join message appears showing a Fire or Tide assignment, with the configured sound and particle.
3. Run `/aspects` (or `/aspects info`): confirm it shows the correct Aspect, rarity, and description.
4. Disconnect and reconnect: confirm the same Aspect is still assigned (no silent reroll).
5. As an op, run `/aspects admin set <player> fire` then `/aspects admin set <player> tide`: confirm the target's Aspect changes each time and is reflected in `/aspects info`.
6. As an op, run `/aspects admin reroll <player>` several times: confirm the Aspect changes to something different each time (since only two Aspects are enabled, it should alternate between fire and tide).
7. With a Fire Aspect: sneak + swap hands (F key by default) with an empty main hand — confirm a short wind-up, then nearby enemy players take damage and catch fire, the caster takes no damage, and the ability goes on cooldown (retrying immediately shows a cooldown action-bar message).
8. With a Tide Aspect: sneak + swap hands with an empty main hand while damaged — confirm health increases by the configured amount without exceeding max health, and the ability goes on cooldown.
9. Take fire/lava damage with the Fire Aspect equipped: confirm no damage is taken from fire/lava specifically, but other damage (e.g. fall damage) still applies normally.
10. Swim underwater with the Tide Aspect equipped: confirm no drowning.
11. Die and respawn: confirm the Aspect is unchanged and its passive (fire resistance / water breathing) is reapplied after respawn.
12. Restart the server: confirm all assigned Aspects and any active cooldowns survive the restart.
13. Try sneak + swap-hands while holding any non-empty item in the main hand, or without sneaking, or while a GUI/inventory is open: confirm the ability does **not** activate (and, for the "not sneaking" / "non-empty hand" cases, confirm the normal vanilla item swap still happens instead).

Record the outcome of this checklist to the user directly; do not mark Phase 1 "done" without it actually having been run.

---

## Post-implementation hardening (final whole-branch review)

After Task 15, a final whole-branch review (most-capable-model, base `76cbd3f`..`a098b20`) found five Important-severity issues spanning multiple tasks' code. These were fixed in a single follow-up pass rather than by rewriting this plan's history, since they change shapes this plan originally specified. The task sections above are left as a historical record of what each task actually built and had reviewed; this section records what changed afterward and why.

1. **`SQLitePlayerDataRepository.loadPlayer` (Task 6) silently degraded a DB read failure to "no data found"**, which `AspectManager.loadOrAssign` (Task 12) then treated as "new player," overwriting a real saved Aspect. Fixed: `loadPlayer` now fails the returned future on `SQLException` instead of returning `Optional.empty()`; `loadOrAssign` leaves the player unassigned and logs on failure instead of reassigning.
2. **Passive potion effects (Tasks 10-11) were never cleared on plugin disable/reload**, only on player quit, so a normal shutdown left permanent hidden effects in a player's vanilla data; a forced reassignment of an invalid/disabled stored Aspect (Task 12) didn't clear whatever passive the player previously had either. Fixed: `AspectsPlugin.onDisable` (Task 15) now unloads all online players before closing the repository; `onEnable` loads/assigns Aspects for already-online players; `AspectManager`'s invalid-aspect reassignment path defensively clears every registered aspect's passive first.
3. **Async races in the join flow (Task 12)**: the main-thread continuation never checked `player.isOnline()`, and an Aspect was published to `assigned` before its cooldowns were loaded, opening a window where a cooldown started locally could be wiped by a late-arriving snapshot. Fixed: player-data and cooldown loads are now combined (`thenCombine`) into one dispatch to the main thread, which checks `isOnline()` first and loads cooldowns into the cache before publishing the Aspect. `AbilityCooldownManager.loadIntoCache` (Task 7) now merges per-ability expiries (keeping the later one) instead of replacing the whole map.
4. **Default first-join particle `"TOTEM"` (Task 3) doesn't exist in Paper 1.21.11** (the real constant is `TOTEM_OF_UNDYING`). Fixed in both `config.yml` and `AspectsConfigParser`'s fallback default.
5. **Ability activation failure messages (Task 9) were hard-coded English literals**, bypassing the `messages.yml` keys (Task 5) that already existed for this. Fixed: `AbilityManager.ActivationResult` now carries a `messageKey` + `placeholders` map instead of a rendered string; `ActivationListener` (Task 13) resolves the key via `ConfigManager.message(...)` and renders it with `MessageUtil`, which required widening its constructor parameter from `JavaPlugin` to `AspectsPlugin`. Two new keys (`ability.unavailable`, `ability.activation-failed`) were added to `messages.yml` for failure paths that had no key before.

All other review findings were Minor (cosmetic/edge-case) and are left for the user to triage before or during Phase 2; they don't block calling Phase 1 complete.

## Live-server verification and the activation trigger fix

The review above raised a concern (unverified at the time) that `PlayerInteractEvent`'s `RIGHT_CLICK_AIR` might not fire reliably for an empty-hand right-click on open air. This was tested directly: a real Paper 1.21.11 server was run in Docker (`itzg/minecraft-server`) with the built plugin jar, driven by a scripted test client (mineflayer), with temporary server-side event logging added to `ActivationListener` to see exactly what Bukkit reported.

**Confirmed:** a sneak + right-click with an empty hand while looking at open air (nothing within ~4.5 blocks) produces **zero** `PlayerInteractEvent` server-side, even though the client packet is genuinely sent — a real, long-documented Bukkit platform limitation. The same action while looking at a nearby block fired correctly (`RIGHT_CLICK_BLOCK`, `sneaking=true`, `mainHand=AIR` — exactly the conditions the listener checked for). This meant the original trigger only worked when aiming at terrain, not in open space — a real problem for any ability meant to be cast while airborne, swimming, or in an open field.

Per the user's direction, the trigger was changed from right-click to **sneak + swap-hands (`PlayerSwapHandItemsEvent`, the F key by default)**, which always fires regardless of what's around the player — no line-of-sight or range dependency. The activation *conditions* (sneaking, empty main hand, debounce, cooldown, all of `AbilityManager.tryActivate`'s gating) are unchanged; only the triggering input event changed. This was re-verified against the same live server setup: swap-hands while looking at open air now correctly starts the ability's cooldown (confirmed via the SQLite `cooldowns` table), including the exact scenario that was previously broken.

The same Docker-based test also independently re-confirmed, on a real server, several items from the manual verification checklist above: clean startup with no exceptions, correct generation of all three config files (including the `TOTEM_OF_UNDYING` particle fix), first-join Aspect assignment and announcement, SQLite persistence of the assignment, and correct behavior of `/aspects`, `/aspects admin set`, and `/aspects admin reroll` from the console against an online/offline player. The full manual checklist (including death/respawn, restart survival, and real PvP/heal verification with a real client) has still not been run and remains the user's to do before relying on this in production.
