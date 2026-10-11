# Aspects Plugin — Phase 3 Batch 3b (Main Aspects GUI, Browser, Details) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the three purely-informational/navigational player GUIs from the original spec — the main `/aspects` menu, the Aspect browser, and a single-Aspect details view — completing Phase 3. No gameplay logic changes; this batch is read-only UI built on the `AspectsGuiHolder`/`InventoryListener` infrastructure from Batch 3a.

**Architecture:** `AspectRarity` gains a `color()` method and `AspectDefinition` gains an `icon` field (both genuine gaps found during Batch 3a's final review — rarity coloring was never implemented anywhere, and no Aspect had a GUI-representable icon). Three new GUI classes are added, each implementing the existing `AspectsGuiHolder` interface and relying on the existing shared `InventoryListener` for click-cancellation security — no new listener infrastructure is needed. `/aspects` (no args) changes from printing text to opening `MainAspectGUI`; the old text behavior moves to a new `/aspects info` subcommand, matching the original spec's command list exactly.

**Tech Stack:** Java 21, Paper API `1.21.11-R0.1-SNAPSHOT`, Maven, JUnit 5.

## Global Constraints

- Every commit ends with exactly: `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>` — regardless of which model generates it. This is a fixed project convention; implementers must not substitute their own model name.
- Every GUI class implements `com.aspects.smp.gui.AspectsGuiHolder` (from Batch 3a) and relies on the existing `com.aspects.smp.listener.InventoryListener` for click/drag cancellation — do not add any new `@EventHandler` for `InventoryClickEvent`/`InventoryCloseEvent`/`InventoryDragEvent` anywhere in this batch; that infrastructure already exists and covers every GUI automatically.
- Clicking an Aspect's entry in the Browser GUI must only ever open the Details GUI for that Aspect — it must never change the viewing player's own assigned Aspect. This is explicit in the original spec: "Clicking another Aspect should show its details but must not change the player's Aspect."
- Pure, branch-containing decision logic (no Bukkit API calls) gets direct unit tests, exactly like `AspectsConfigParser`'s existing tests. GUI layout/click-handling code that calls real Bukkit APIs (`Inventory`, `ItemStack`, `Player`) is manual-verification-only, matching every other such class in this project (including every GUI class from Batch 3a).
- Build/test command, used identically in every task:
  ```
  export JAVA_HOME="/home/erikkalstrom/.local/share/flatpak/runtime/org.freedesktop.Sdk.Extension.openjdk21/x86_64/26.08/d4d21c831d4b2abcc5c2c1f32d7ac4a5cac886db0328a94a259021e26e93689a/files/jvm/openjdk-21"
  export PATH="$JAVA_HOME/bin:/tmp/apache-maven-3.9.9/bin:$PATH"
  ```
- Starting test count before this plan: 73. Each task below states the expected running total.
- The Admin GUI, the full admin/permission command suite, the Aspect Selector item, and WorldGuard integration remain out of scope (Phase 4/5), per the Phase 3 design doc.

---

### Task 1: `AspectRarity.color()`, `AspectDefinition.icon`, and the `aspects.yml` icon keys

**Files:**
- Modify: `src/main/java/com/aspects/smp/aspect/AspectRarity.java`
- Modify: `src/main/java/com/aspects/smp/aspect/AspectDefinition.java`
- Modify: `src/main/java/com/aspects/smp/config/AspectsConfigParser.java`
- Modify: `src/main/java/com/aspects/smp/command/AspectsCommand.java`
- Modify: `src/main/resources/aspects.yml`
- Test: `src/test/java/com/aspects/smp/config/AspectsConfigParserTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `AspectRarity.color() -> String` (a MiniMessage color tag), `AspectDefinition.icon() -> Material` (record component). Used by Tasks 2, 3, and 4 (every new GUI shows an Aspect's icon and rarity color).

- [ ] **Step 1: Add `color()` to `AspectRarity`**

Replace the full contents of `src/main/java/com/aspects/smp/aspect/AspectRarity.java` with:
```java
package com.aspects.smp.aspect;

public enum AspectRarity {
    COMMON("white"),
    UNCOMMON("green"),
    RARE("aqua"),
    EPIC("light_purple"),
    LEGENDARY("gold");

    private final String color;

    AspectRarity(String color) {
        this.color = color;
    }

    public String color() {
        return color;
    }
}
```

- [ ] **Step 2: Add the `icon` field to `AspectDefinition`**

Replace the full contents of `src/main/java/com/aspects/smp/aspect/AspectDefinition.java` with:
```java
package com.aspects.smp.aspect;

import org.bukkit.Material;

import java.util.Map;

public record AspectDefinition(
        String id,
        String displayName,
        String description,
        AspectRarity rarity,
        double weight,
        boolean enabled,
        Material icon,
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

- [ ] **Step 3: Write the failing tests for icon parsing**

Append these tests to the end of `AspectsConfigParserTest` (inside the closing brace, after the last existing test):

```java

    @Test
    void parseAspects_parsesValidIcon() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "aspects:\n" +
                "  fire:\n" +
                "    enabled: true\n" +
                "    icon: BLAZE_POWDER\n"));
        List<String> errors = new ArrayList<>();

        Map<String, AspectDefinition> aspects = AspectsConfigParser.parseAspects(yml, errors);

        assertTrue(errors.isEmpty());
        assertEquals(org.bukkit.Material.BLAZE_POWDER, aspects.get("fire").icon());
    }

    @Test
    void parseAspects_rejectsUnknownIcon() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "aspects:\n" +
                "  fire:\n" +
                "    enabled: true\n" +
                "    icon: NOT_A_REAL_MATERIAL\n"));
        List<String> errors = new ArrayList<>();

        AspectsConfigParser.parseAspects(yml, errors);

        assertTrue(errors.stream().anyMatch(e -> e.contains("icon") && e.contains("fire")));
    }

    @Test
    void parseAspects_defaultsIconToStoneWhenMissing() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "aspects:\n" +
                "  fire:\n" +
                "    enabled: true\n"));
        List<String> errors = new ArrayList<>();

        Map<String, AspectDefinition> aspects = AspectsConfigParser.parseAspects(yml, errors);

        assertTrue(errors.isEmpty());
        assertEquals(org.bukkit.Material.STONE, aspects.get("fire").icon());
    }
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `mvn -q test -Dtest=AspectsConfigParserTest`
Expected: FAIL — compile error, `AspectDefinition` constructor doesn't match (missing `icon` argument) and no `icon()` accessor yet.

- [ ] **Step 5: Update `AspectsConfigParser.parseAspects` to read and validate `icon`**

In `src/main/java/com/aspects/smp/config/AspectsConfigParser.java`, change:
```java
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
```
to:
```java
            boolean enabled = aspectSection.getBoolean("enabled", false);
            String displayName = aspectSection.getString("display-name", id);
            String description = aspectSection.getString("description", "");
            String rarityRaw = aspectSection.getString("rarity", "COMMON");
            double weight = aspectSection.getDouble("weight", 0);
            String iconRaw = aspectSection.getString("icon", "STONE");

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
            Material icon = Material.matchMaterial(iconRaw);
            if (icon == null) {
                errors.add("aspects." + id + ".icon has unknown material '" + iconRaw + "'");
                icon = Material.STONE;
            }

            Map<String, Object> settings = aspectSection.getValues(true);
            result.put(id, new AspectDefinition(id, displayName, description, rarity, weight, enabled, icon, settings));
```
(The `import org.bukkit.Material;` import already exists in this file from Batch 3a's `parseRecipes` work.)

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn -q test -Dtest=AspectsConfigParserTest`
Expected: PASS (all tests in this class, including the 3 new ones).

- [ ] **Step 7: Add icon keys to `aspects.yml`**

Add one `icon:` line to each of the 8 Aspect blocks in `src/main/resources/aspects.yml`, right after each `weight:` line:

```yaml
  fire:
    ...
    weight: 20
    icon: BLAZE_POWDER
    ...

  tide:
    ...
    weight: 40
    icon: HEART_OF_THE_SEA
    ...

  frost:
    ...
    weight: 20
    icon: PACKED_ICE
    ...

  thunder:
    ...
    weight: 10
    icon: TRIDENT
    ...

  void:
    ...
    weight: 5
    icon: ENDER_PEARL
    ...

  earth:
    ...
    weight: 20
    icon: SHIELD
    ...

  wind:
    ...
    weight: 40
    icon: FEATHER
    ...

  blood:
    ...
    weight: 10
    icon: REDSTONE
    ...
```
(Insert each `icon:` line in its corresponding block without otherwise altering the file — the `...` above marks unchanged surrounding lines, not literal text to write.)

- [ ] **Step 8: Retrofit `AspectsCommand.handleInfo` to use the new rarity color**

In `src/main/java/com/aspects/smp/command/AspectsCommand.java`, change:
```java
        player.sendMessage(MessageUtil.parse(
                "<dark_purple>Rarity:</dark_purple> <white>" + aspect.definition().rarity().name() + "</white>"));
```
to:
```java
        player.sendMessage(MessageUtil.parse(
                "<dark_purple>Rarity:</dark_purple> <" + aspect.definition().rarity().color() + ">" +
                        aspect.definition().rarity().name() + "</" + aspect.definition().rarity().color() + ">"));
```
(This is the first and, as of this batch, only other place rarity is ever printed, so this is the complete retrofit — the original spec's "rarity colors are consistent throughout messages and GUIs" is now satisfied everywhere rarity appears.)

- [ ] **Step 9: Run the full test suite to confirm no regressions**

Run: `mvn -q test`
Expected: 76 tests total (73 before this task, plus these 3 new ones).

- [ ] **Step 10: Build the jar**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`, jar built.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/aspects/smp/aspect/AspectRarity.java src/main/java/com/aspects/smp/aspect/AspectDefinition.java src/main/java/com/aspects/smp/config/AspectsConfigParser.java src/main/java/com/aspects/smp/command/AspectsCommand.java src/main/resources/aspects.yml src/test/java/com/aspects/smp/config/AspectsConfigParserTest.java
git commit -m "$(cat <<'EOF'
Add AspectRarity.color() and AspectDefinition.icon for GUI display

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: `AspectDetailsGUI`

**Files:**
- Create: `src/main/java/com/aspects/smp/gui/AspectDetailsGUI.java`
- Modify: `src/main/resources/messages.yml`

**Interfaces:**
- Consumes: `AspectsGuiHolder` (Batch 3a); `AspectDefinition.icon()`/`AspectRarity.color()` (Task 1).
- Produces: `new AspectDetailsGUI(AspectsPlugin, Player, Aspect, Runnable onBack).open()`, used by Task 3's `AspectBrowserGUI`. The `onBack` callback lets the Browser GUI re-open itself without `AspectDetailsGUI` needing a compile-time dependency on `AspectBrowserGUI` (avoiding a circular class dependency, since the Browser also needs to reference `AspectDetailsGUI` to open it).

A single-Aspect, read-only detail view. Makes no state changes at all except the Back button, which never modifies any Aspect data — it only closes this GUI and invokes `onBack.run()`.

No test file: pure Bukkit GUI code, manual-verification-only, matching every GUI class from Batch 3a.

- [ ] **Step 1: Add new message keys to `src/main/resources/messages.yml`**

Add this new top-level section at the end of the file (after the existing `gui:` section from Batch 3a):
```yaml
  details:
    back-name: "<yellow>« Back</yellow>"
    passive-label: "<gray>Passive:</gray>"
    active-label: "<gray>Active:</gray>"
    cooldown-label: "<gray>Cooldown:</gray>"
```
(This nests under the existing `gui:` key from Batch 3a — the file should now have `gui: reroll-confirm: ... details: ...` as two children of `gui:`.)

- [ ] **Step 2: Create `src/main/java/com/aspects/smp/gui/AspectDetailsGUI.java`**

```java
package com.aspects.smp.gui;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.Aspect;
import com.aspects.smp.util.MessageUtil;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class AspectDetailsGUI implements AspectsGuiHolder {
    private static final int SIZE = 9;
    private static final int DETAIL_SLOT = 4;
    private static final int BACK_SLOT = 8;

    private final AspectsPlugin plugin;
    private final Player player;
    private final Aspect aspect;
    private final Runnable onBack;
    private final Inventory inventory;

    public AspectDetailsGUI(AspectsPlugin plugin, Player player, Aspect aspect, Runnable onBack) {
        this.plugin = plugin;
        this.player = player;
        this.aspect = aspect;
        this.onBack = onBack;
        this.inventory = Bukkit.createInventory(this, SIZE,
                MessageUtil.parse(aspect.definition().displayName()));
        populate();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        player.openInventory(inventory);
    }

    private void populate() {
        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }
        inventory.setItem(DETAIL_SLOT, detailItem());
        inventory.setItem(BACK_SLOT, namedItem(Material.ARROW, plugin.getConfigManager().message("gui.details.back-name")));
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(name).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack detailItem() {
        ItemStack item = new ItemStack(aspect.definition().icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(aspect.definition().displayName()).decoration(TextDecoration.ITALIC, false));

        List<String> lore = new ArrayList<>();
        lore.add("<" + aspect.definition().rarity().color() + ">" + aspect.definition().rarity().name() + "</" +
                aspect.definition().rarity().color() + ">");
        lore.add(" ");
        lore.add(aspect.definition().description());
        lore.add(" ");
        lore.add(plugin.getConfigManager().message("gui.details.passive-label") + " " + passiveSummary());
        lore.add(plugin.getConfigManager().message("gui.details.active-label") + " " + activeSummary());
        lore.add(plugin.getConfigManager().message("gui.details.cooldown-label") + " " +
                (long) aspect.definition().getDouble("active.cooldown-seconds", 20.0) + "s");

        meta.lore(lore.stream().map(line -> MessageUtil.parse(line).decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private String passiveSummary() {
        return aspect.definition().id().substring(0, 1).toUpperCase() + aspect.definition().id().substring(1) + " passive";
    }

    private String activeSummary() {
        return aspect.definition().id().substring(0, 1).toUpperCase() + aspect.definition().id().substring(1) + " active ability";
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!inventory.equals(event.getClickedInventory())) {
            return;
        }
        if (event.getSlot() == BACK_SLOT) {
            player.closeInventory();
            onBack.run();
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        // No state to clean up — this GUI never mutates anything.
    }
}
```

**Note for the implementer:** `passiveSummary()`/`activeSummary()` are deliberately generic placeholders ("Fire passive", "Fire active ability") rather than per-Aspect human-readable ability descriptions, because no per-Aspect ability description text exists anywhere in the codebase today (`AspectDefinition` has no such field, and `aspects.yml` only has a top-level `description` covering the Aspect as a whole, not each ability separately). Writing real per-ability descriptions for all 8 Aspects is a content-authoring task, not a code task, and is out of scope for this plan — implement exactly as shown; do not invent per-Aspect ability text.

- [ ] **Step 3: Build**

Run: `mvn -q package -DskipTests`
Expected: exit 0.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/messages.yml src/main/java/com/aspects/smp/gui/AspectDetailsGUI.java
git commit -m "$(cat <<'EOF'
Add AspectDetailsGUI

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: `AspectBrowserGUI`

**Files:**
- Create: `src/main/java/com/aspects/smp/gui/AspectBrowserGUI.java`
- Modify: `src/main/resources/messages.yml`

**Interfaces:**
- Consumes: `AspectsGuiHolder` (Batch 3a); `AspectDetailsGUI` (Task 2); `AspectRegistry.enabled()` (Phase 1) via `AspectsPlugin` — see Step 1 below for how to reach it.
- Produces: `new AspectBrowserGUI(AspectsPlugin, Player, Runnable onBack).open()`, used by Task 4's `MainAspectGUI`.

Clicking an Aspect's entry opens `AspectDetailsGUI` for that Aspect — it never changes the viewing player's own Aspect. A "Back" button returns to the caller (`MainAspectGUI`) via the same `Runnable onBack` pattern used in Task 2.

No test file: pure Bukkit GUI code, manual-verification-only.

- [ ] **Step 1: Find how to list all enabled Aspects**

`AspectsPlugin` does not currently expose its `AspectRegistry` directly — only `getAspectManager()`, which deals in per-player assignment, not the full registry. Read `src/main/java/com/aspects/smp/AspectsPlugin.java` to confirm there is a private `aspectRegistry` field with no public getter, then add one:

Add this getter next to the existing `getAspectManager()`:
```java
    public AspectRegistry getAspectRegistry() {
        return aspectRegistry;
    }
```
(`AspectRegistry` is already imported in this file.)

- [ ] **Step 2: Add a new message key to `src/main/resources/messages.yml`**

Add this key inside the existing `gui:` section, as a sibling of `reroll-confirm:` and `details:`:
```yaml
  browser:
    back-name: "<yellow>« Back</yellow>"
```

- [ ] **Step 3: Create `src/main/java/com/aspects/smp/gui/AspectBrowserGUI.java`**

```java
package com.aspects.smp.gui;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.Aspect;
import com.aspects.smp.util.MessageUtil;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AspectBrowserGUI implements AspectsGuiHolder {
    private static final int SIZE = 54;
    private static final int BACK_SLOT = 49;

    private final AspectsPlugin plugin;
    private final Player player;
    private final Runnable onBack;
    private final Inventory inventory;
    private final Map<Integer, Aspect> aspectBySlot = new HashMap<>();

    public AspectBrowserGUI(AspectsPlugin plugin, Player player, Runnable onBack) {
        this.plugin = plugin;
        this.player = player;
        this.onBack = onBack;
        this.inventory = Bukkit.createInventory(this, SIZE,
                MessageUtil.parse("<dark_purple>Aspect Browser</dark_purple>"));
        populate();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        player.openInventory(inventory);
    }

    private void populate() {
        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        List<Aspect> enabled = new ArrayList<>(plugin.getAspectRegistry().enabled());
        int slot = 0;
        for (Aspect aspect : enabled) {
            if (slot >= BACK_SLOT) {
                break;
            }
            inventory.setItem(slot, browserItem(aspect));
            aspectBySlot.put(slot, aspect);
            slot++;
        }

        inventory.setItem(BACK_SLOT, namedItem(Material.ARROW, plugin.getConfigManager().message("gui.browser.back-name")));
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(name).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack browserItem(Aspect aspect) {
        ItemStack item = new ItemStack(aspect.definition().icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(aspect.definition().displayName()).decoration(TextDecoration.ITALIC, false));
        String capitalizedId = aspect.definition().id().substring(0, 1).toUpperCase() + aspect.definition().id().substring(1);
        List<String> lore = List.of(
                "<" + aspect.definition().rarity().color() + ">" + aspect.definition().rarity().name() + "</" +
                        aspect.definition().rarity().color() + ">",
                aspect.definition().description(),
                " ",
                "<gray>Passive:</gray> " + capitalizedId + " passive",
                "<gray>Active:</gray> " + capitalizedId + " active ability",
                "<gray>Cooldown:</gray> " + (long) aspect.definition().getDouble("active.cooldown-seconds", 20.0) + "s");
        meta.lore(lore.stream().map(line -> MessageUtil.parse(line).decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!inventory.equals(event.getClickedInventory())) {
            return;
        }
        int slot = event.getSlot();
        if (slot == BACK_SLOT) {
            player.closeInventory();
            onBack.run();
            return;
        }
        Aspect clicked = aspectBySlot.get(slot);
        if (clicked != null) {
            player.closeInventory();
            new AspectDetailsGUI(plugin, player, clicked, () -> new AspectBrowserGUI(plugin, player, onBack).open()).open();
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        // No state to clean up — this GUI never mutates anything.
    }
}
```

- [ ] **Step 4: Build**

Run: `mvn -q package -DskipTests`
Expected: exit 0.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/aspects/smp/AspectsPlugin.java src/main/resources/messages.yml src/main/java/com/aspects/smp/gui/AspectBrowserGUI.java
git commit -m "$(cat <<'EOF'
Add AspectBrowserGUI

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: `MainAspectGUI`

**Files:**
- Create: `src/main/java/com/aspects/smp/gui/MainAspectGUI.java`
- Modify: `src/main/java/com/aspects/smp/AspectsPlugin.java`
- Modify: `src/main/resources/messages.yml`

**Interfaces:**
- Consumes: `AspectsGuiHolder` (Batch 3a); `AspectBrowserGUI` (Task 3); `AbilityCooldownManager.remainingMillis` (Phase 1) via a new `AspectsPlugin.getCooldownManager()` getter; `RecipesConfig` (Batch 3a Task 1) for the craft-info lore.
- Produces: `new MainAspectGUI(AspectsPlugin, Player).open()`, used by Task 5's `AspectsCommand` change.

- [ ] **Step 1: Add a `getCooldownManager()` getter to `AspectsPlugin`**

`AspectsPlugin` has a private `cooldownManager` field (constructed in `onEnable`) with no public getter. Add one next to the existing `getAspectManager()`:
```java
    public AbilityCooldownManager getCooldownManager() {
        return cooldownManager;
    }
```
(`AbilityCooldownManager` is already imported in this file.)

- [ ] **Step 2: Add new message keys to `src/main/resources/messages.yml`**

Add this key inside the existing `gui:` section, as a sibling of `reroll-confirm:`, `details:`, and `browser:`:
```yaml
  main:
    title: "<dark_purple>Your Aspect</dark_purple>"
    browse-name: "<aqua>Browse Aspects</aqua>"
    craft-info-name: "<gray>How to craft a Reroller</gray>"
    cooldown-ready: "<green>Ready</green>"
    cooldown-remaining: "<red>{seconds}s remaining</red>"
```

- [ ] **Step 3: Create `src/main/java/com/aspects/smp/gui/MainAspectGUI.java`**

```java
package com.aspects.smp.gui;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.Aspect;
import com.aspects.smp.config.RecipesConfig;
import com.aspects.smp.util.MessageUtil;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MainAspectGUI implements AspectsGuiHolder {
    private static final int SIZE = 27;
    private static final int CURRENT_ASPECT_SLOT = 13;
    private static final int BROWSE_SLOT = 11;
    private static final int CRAFT_INFO_SLOT = 15;

    private final AspectsPlugin plugin;
    private final Player player;
    private final Inventory inventory;

    public MainAspectGUI(AspectsPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, SIZE, MessageUtil.parse(plugin.getConfigManager().message("gui.main.title")));
        populate();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        player.openInventory(inventory);
    }

    private void populate() {
        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }
        inventory.setItem(CURRENT_ASPECT_SLOT, currentAspectItem());
        inventory.setItem(BROWSE_SLOT, namedItem(Material.BOOK, plugin.getConfigManager().message("gui.main.browse-name")));
        inventory.setItem(CRAFT_INFO_SLOT, craftInfoItem());
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(name).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack currentAspectItem() {
        Aspect aspect = plugin.getAspectManager().getAspect(player.getUniqueId()).orElse(null);
        if (aspect == null) {
            return namedItem(Material.BARRIER, plugin.getConfigManager().message("ability.no-aspect"));
        }

        ItemStack item = new ItemStack(aspect.definition().icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(aspect.definition().displayName()).decoration(TextDecoration.ITALIC, false));

        long remainingMillis = plugin.getCooldownManager().remainingMillis(player.getUniqueId(), aspect.id());
        String cooldownLine = remainingMillis > 0
                ? plugin.getConfigManager().message("gui.main.cooldown-remaining")
                        .replace("{seconds}", String.valueOf((remainingMillis + 999) / 1000))
                : plugin.getConfigManager().message("gui.main.cooldown-ready");

        List<String> lore = new ArrayList<>();
        lore.add("<" + aspect.definition().rarity().color() + ">" + aspect.definition().rarity().name() + "</" +
                aspect.definition().rarity().color() + ">");
        lore.add(aspect.definition().description());
        lore.add(" ");
        lore.add(cooldownLine);
        meta.lore(lore.stream().map(line -> MessageUtil.parse(line).decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack craftInfoItem() {
        ItemStack item = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(plugin.getConfigManager().message("gui.main.craft-info-name"))
                .decoration(TextDecoration.ITALIC, false));

        RecipesConfig recipes = plugin.getConfigManager().recipes();
        List<String> lore = new ArrayList<>();
        for (String row : recipes.rerollerPattern()) {
            StringBuilder line = new StringBuilder("<gray>");
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (i > 0) {
                    line.append(", ");
                }
                if (c == ' ') {
                    line.append("(empty)");
                } else {
                    Material material = recipes.rerollerIngredients().get(c);
                    line.append(material != null ? material.name() : "?");
                }
            }
            line.append("</gray>");
            lore.add(line.toString());
        }
        meta.lore(lore.stream().map(line -> MessageUtil.parse(line).decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!inventory.equals(event.getClickedInventory())) {
            return;
        }
        if (event.getSlot() == BROWSE_SLOT) {
            player.closeInventory();
            new AspectBrowserGUI(plugin, player, () -> new MainAspectGUI(plugin, player).open()).open();
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        // No state to clean up — this GUI never mutates anything.
    }
}
```

- [ ] **Step 4: Build**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`, all 76 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/aspects/smp/gui/MainAspectGUI.java src/main/java/com/aspects/smp/AspectsPlugin.java src/main/resources/messages.yml
git commit -m "$(cat <<'EOF'
Add MainAspectGUI

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Wire `/aspects` to open the GUI, extract `/aspects info`

**Files:**
- Modify: `src/main/java/com/aspects/smp/command/AspectsCommand.java`

**Interfaces:**
- Consumes: `MainAspectGUI` (Task 4).
- Produces: nothing new for later tasks — this is the final integration point for Batch 3b.

This is a pure extraction plus one new branch: `/aspects` (no args) now opens the GUI; `/aspects info` gets the exact existing text behavior, unchanged in substance.

- [ ] **Step 1: Read the current `src/main/java/com/aspects/smp/command/AspectsCommand.java`**

(It should match the "Task 1, Step 8" retrofit — the rarity line using `rarity().color()` — and nothing from Tasks 2-4 of this plan, since those didn't touch this file. If it looks different from what this plan expects, stop and report BLOCKED rather than guessing.)

- [ ] **Step 2: Add the import**

Add this import alongside the existing ones:
```java
import com.aspects.smp.gui.MainAspectGUI;
```

- [ ] **Step 3: Change `onCommand` and split out `handleInfo`**

Change:
```java
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            return adminCommand.handle(sender, shift(args));
        }
        return handleInfo(sender);
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
        return handleOpenMenu(sender);
    }

    private boolean handleOpenMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>Only players can open the Aspects menu. Use /aspects admin debug <player> from console.</red>"));
            return true;
        }
        if (!player.hasPermission("aspects.use")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to use this command.</red>"));
            return true;
        }
        new MainAspectGUI(plugin, player).open();
        return true;
    }
```
(`handleInfo` stays exactly as it already is — untouched, just no longer the default no-args branch.)

- [ ] **Step 4: Build and test**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`, all 76 tests pass, jar built.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/aspects/smp/command/AspectsCommand.java
git commit -m "$(cat <<'EOF'
Open MainAspectGUI from /aspects, move text info to /aspects info

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 6: Note for the controller, not this task's implementer**

Live verification against a running Paper 1.21.11 server is the controller's responsibility after this task's review passes, the same way every prior batch's final wiring task was handled — do not attempt this as the task implementer; there is no running server in that environment. Specifically verify: `/aspects` opens `MainAspectGUI` showing the correct current Aspect, rarity color, and cooldown state; the Browse button opens `AspectBrowserGUI` listing all 8 enabled Aspects with their icons; clicking an Aspect in the Browser opens `AspectDetailsGUI` for that Aspect WITHOUT changing the viewing player's own assigned Aspect; Back buttons correctly navigate Details → Browser → Main; `/aspects info` still prints the original text summary; all GUI clicks are fully locked down (no item extraction via shift-click/number-key-swap/drag, consistent with Batch 3a's `InventoryListener` already covering this automatically).
