# Aspects Plugin — Phase 3 Batch 3a (Reroller Item, Crafting, Confirmation) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the complete, player-facing "craft a Reroller, right-click it, confirm, get a new random Aspect" mechanic — the item, its crafting recipe, and the confirmation GUI — with no informational GUIs yet (those are Batch 3b).

**Architecture:** A new `item/` package owns the Reroller's identity (a `PersistentDataContainer`-tagged `AMETHYST_SHARD`, never identified by name/lore). A new `recipe/` package registers its shaped crafting recipe from a new `recipes.yml` config file. A new `gui/` package (plus one shared `listener/InventoryListener`) implements the confirmation flow with a hard "every click is cancelled unless our code explicitly allows it" security model. `AspectManager` is refactored so the existing, already-tested `adminReroll` and the new `rerollViaItem` share one private implementation.

**Tech Stack:** Java 21, Paper API `1.21.11-R0.1-SNAPSHOT`, Maven, JUnit 5.

## Global Constraints

- Every commit ends with exactly: `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>` — regardless of which model generates it. This is a fixed project convention; implementers must not substitute their own model name.
- A Reroller item is identified **only** via its `PersistentDataContainer` tag (`NamespacedKey(plugin, "reroller")`, `PersistentDataType.BOOLEAN`, value `true`). Never check display name or lore to decide whether an `ItemStack` is a valid Reroller — this is an explicit, repeated requirement in the original spec ("never identify a valid reroller based solely on its name or lore," "prevent renamed or forged ordinary amethyst shards from functioning as rerollers").
- A reroll triggered by consuming a Reroller item must be **atomic**: if `AspectManager.rerollViaItem` returns empty (no eligible Aspect), the item must NOT be consumed. The item is only ever subtracted after a successful, non-empty result.
- Every new GUI's `InventoryClickEvent` must be fully cancelled by the shared `InventoryListener` before any GUI-specific logic runs — a GUI class is never given the chance to "forget" to cancel a click.
- Bukkit-API-dependent code (anything touching `ItemStack`, `ItemMeta`, `Inventory`, `Player`, Bukkit events) is manual-verification-only in this project — there is no Bukkit mocking anywhere in the existing test suite, and this plan does not introduce any. Only pure config-parsing logic (no Bukkit `ItemStack`/`Inventory` involved — `Material.matchMaterial` on a plain enum is fine, it needs no running server) gets direct unit tests, exactly like the existing `AspectsConfigParserTest`.
- Build/test command, used identically in every task:
  ```
  export JAVA_HOME="/home/erikkalstrom/.local/share/flatpak/runtime/org.freedesktop.Sdk.Extension.openjdk21/x86_64/26.08/d4d21c831d4b2abcc5c2c1f32d7ac4a5cac886db0328a94a259021e26e93689a/files/jvm/openjdk-21"
  export PATH="$JAVA_HOME/bin:/tmp/apache-maven-3.9.9/bin:$PATH"
  ```
- Starting test count before this plan: 64. Each task below states the expected running total.
- The Admin GUI, the rest of the admin command suite, the Aspect Selector item, and WorldGuard integration are explicitly out of scope — see the design doc's "What's deliberately out of scope here."

---

### Task 1: `RecipesConfig`, its parser, and `recipes.yml`

**Files:**
- Create: `src/main/resources/recipes.yml`
- Create: `src/main/java/com/aspects/smp/config/RecipesConfig.java`
- Modify: `src/main/java/com/aspects/smp/config/AspectsConfigParser.java`
- Modify: `src/main/java/com/aspects/smp/config/ConfigManager.java`
- Test: `src/test/java/com/aspects/smp/config/AspectsConfigParserTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `RecipesConfig` record (`rerollerEnabled: boolean`, `rerollerPattern: List<String>`, `rerollerIngredients: Map<Character, Material>`, `rerollerResultAmount: int`); `ConfigManager.recipes() -> RecipesConfig`, used by Task 6's `RerollerRecipeManager`.

- [ ] **Step 1: Create `src/main/resources/recipes.yml`**

```yaml
# Crafting recipe for the Aspect Reroller item.
reroller:
  enabled: true
  pattern:
    - "ADA"
    - "DND"
    - "ADA"
  ingredients:
    A: AMETHYST_SHARD
    D: DIAMOND
    N: NETHER_STAR
  result-amount: 1
```

- [ ] **Step 2: Create `src/main/java/com/aspects/smp/config/RecipesConfig.java`**

```java
package com.aspects.smp.config;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public record RecipesConfig(
        boolean rerollerEnabled,
        List<String> rerollerPattern,
        Map<Character, Material> rerollerIngredients,
        int rerollerResultAmount
) {
}
```

- [ ] **Step 3: Write the failing tests**

Append these tests to the end of `AspectsConfigParserTest` (inside the closing brace, after the last existing test):

```java

    @Test
    void parseRecipes_acceptsValidConfig() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "reroller:\n" +
                "  enabled: true\n" +
                "  pattern:\n" +
                "    - \"ADA\"\n" +
                "    - \"DND\"\n" +
                "    - \"ADA\"\n" +
                "  ingredients:\n" +
                "    A: AMETHYST_SHARD\n" +
                "    D: DIAMOND\n" +
                "    N: NETHER_STAR\n" +
                "  result-amount: 1\n"));
        List<String> errors = new ArrayList<>();

        RecipesConfig config = AspectsConfigParser.parseRecipes(yml, errors);

        assertTrue(errors.isEmpty());
        assertTrue(config.rerollerEnabled());
        assertEquals(List.of("ADA", "DND", "ADA"), config.rerollerPattern());
        assertEquals(org.bukkit.Material.AMETHYST_SHARD, config.rerollerIngredients().get('A'));
        assertEquals(org.bukkit.Material.DIAMOND, config.rerollerIngredients().get('D'));
        assertEquals(org.bukkit.Material.NETHER_STAR, config.rerollerIngredients().get('N'));
        assertEquals(1, config.rerollerResultAmount());
    }

    @Test
    void parseRecipes_rejectsPatternWithWrongRowCount() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "reroller:\n" +
                "  pattern:\n" +
                "    - \"ADA\"\n" +
                "    - \"DND\"\n" +
                "  ingredients:\n" +
                "    A: AMETHYST_SHARD\n" +
                "    D: DIAMOND\n"));
        List<String> errors = new ArrayList<>();

        AspectsConfigParser.parseRecipes(yml, errors);

        assertTrue(errors.stream().anyMatch(e -> e.contains("exactly 3 rows")));
    }

    @Test
    void parseRecipes_rejectsRowWithWrongLength() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "reroller:\n" +
                "  pattern:\n" +
                "    - \"AD\"\n" +
                "    - \"DND\"\n" +
                "    - \"ADA\"\n" +
                "  ingredients:\n" +
                "    A: AMETHYST_SHARD\n" +
                "    D: DIAMOND\n" +
                "    N: NETHER_STAR\n"));
        List<String> errors = new ArrayList<>();

        AspectsConfigParser.parseRecipes(yml, errors);

        assertTrue(errors.stream().anyMatch(e -> e.contains("exactly 3 characters")));
    }

    @Test
    void parseRecipes_rejectsUnknownMaterial() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "reroller:\n" +
                "  pattern:\n" +
                "    - \"ADA\"\n" +
                "    - \"DND\"\n" +
                "    - \"ADA\"\n" +
                "  ingredients:\n" +
                "    A: NOT_A_REAL_MATERIAL\n" +
                "    D: DIAMOND\n" +
                "    N: NETHER_STAR\n"));
        List<String> errors = new ArrayList<>();

        AspectsConfigParser.parseRecipes(yml, errors);

        assertTrue(errors.stream().anyMatch(e -> e.contains("unknown material")));
    }

    @Test
    void parseRecipes_rejectsPatternCharacterWithNoIngredient() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "reroller:\n" +
                "  pattern:\n" +
                "    - \"ADA\"\n" +
                "    - \"DXD\"\n" +
                "    - \"ADA\"\n" +
                "  ingredients:\n" +
                "    A: AMETHYST_SHARD\n" +
                "    D: DIAMOND\n"));
        List<String> errors = new ArrayList<>();

        AspectsConfigParser.parseRecipes(yml, errors);

        assertTrue(errors.stream().anyMatch(e -> e.contains("no matching ingredient")));
    }

    @Test
    void parseRecipes_allowsSpaceAsEmptySlot() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "reroller:\n" +
                "  pattern:\n" +
                "    - \"A A\"\n" +
                "    - \"DND\"\n" +
                "    - \"A A\"\n" +
                "  ingredients:\n" +
                "    A: AMETHYST_SHARD\n" +
                "    D: DIAMOND\n" +
                "    N: NETHER_STAR\n"));
        List<String> errors = new ArrayList<>();

        AspectsConfigParser.parseRecipes(yml, errors);

        assertTrue(errors.isEmpty());
    }

    @Test
    void parseRecipes_rejectsResultAmountBelowOne() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "reroller:\n" +
                "  pattern:\n" +
                "    - \"ADA\"\n" +
                "    - \"DND\"\n" +
                "    - \"ADA\"\n" +
                "  ingredients:\n" +
                "    A: AMETHYST_SHARD\n" +
                "    D: DIAMOND\n" +
                "    N: NETHER_STAR\n" +
                "  result-amount: 0\n"));
        List<String> errors = new ArrayList<>();

        RecipesConfig config = AspectsConfigParser.parseRecipes(yml, errors);

        assertTrue(errors.stream().anyMatch(e -> e.contains("result-amount")));
        assertEquals(1, config.rerollerResultAmount());
    }
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `mvn -q test -Dtest=AspectsConfigParserTest`
Expected: FAIL — `AspectsConfigParser.parseRecipes` does not exist.

- [ ] **Step 5: Add `parseRecipes` to `AspectsConfigParser`**

Add this import alongside the existing ones at the top of `AspectsConfigParser.java`:
```java
import org.bukkit.Material;
```

Add this method at the end of the class, just before the final closing brace:

```java

    public static RecipesConfig parseRecipes(FileConfiguration yml, List<String> errors) {
        boolean enabled = yml.getBoolean("reroller.enabled", true);
        List<String> pattern = yml.getStringList("reroller.pattern");
        int resultAmount = yml.getInt("reroller.result-amount", 1);

        if (pattern.size() != 3) {
            errors.add("recipes.yml reroller.pattern must have exactly 3 rows");
            pattern = List.of("ADA", "DND", "ADA");
        } else {
            for (String row : pattern) {
                if (row.length() != 3) {
                    errors.add("recipes.yml reroller.pattern rows must each be exactly 3 characters");
                    break;
                }
            }
        }

        Map<Character, Material> ingredients = new LinkedHashMap<>();
        ConfigurationSection ingredientsSection = yml.getConfigurationSection("reroller.ingredients");
        if (ingredientsSection == null) {
            errors.add("recipes.yml is missing the 'reroller.ingredients' section");
        } else {
            for (String key : ingredientsSection.getKeys(false)) {
                if (key.length() != 1) {
                    errors.add("recipes.yml reroller.ingredients key '" + key + "' must be a single character");
                    continue;
                }
                String materialName = ingredientsSection.getString(key);
                Material material = materialName == null ? null : Material.matchMaterial(materialName);
                if (material == null) {
                    errors.add("recipes.yml reroller.ingredients." + key + " has unknown material '" + materialName + "'");
                    continue;
                }
                ingredients.put(key.charAt(0), material);
            }

            for (String row : pattern) {
                for (char c : row.toCharArray()) {
                    if (c != ' ' && !ingredients.containsKey(c)) {
                        errors.add("recipes.yml reroller.pattern uses character '" + c + "' with no matching ingredient");
                    }
                }
            }
        }

        if (resultAmount < 1) {
            errors.add("recipes.yml reroller.result-amount must be at least 1");
            resultAmount = 1;
        }

        return new RecipesConfig(enabled, pattern, ingredients, resultAmount);
    }
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn -q test -Dtest=AspectsConfigParserTest`
Expected: PASS (all tests in this class, including the 7 new ones).

- [ ] **Step 7: Wire `RecipesConfig` into `ConfigManager`**

In `src/main/java/com/aspects/smp/config/ConfigManager.java`, change:
```java
    private GeneralConfig generalConfig;
    private Map<String, AspectDefinition> aspectDefinitions = Map.of();
    private Map<String, String> messages = Map.of();
```
to:
```java
    private GeneralConfig generalConfig;
    private Map<String, AspectDefinition> aspectDefinitions = Map.of();
    private Map<String, String> messages = Map.of();
    private RecipesConfig recipesConfig;
```

Change:
```java
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
```
to:
```java
    public void loadAll() throws ConfigValidationException {
        plugin.saveDefaultConfig();
        saveResourceIfMissing("aspects.yml");
        saveResourceIfMissing("messages.yml");
        saveResourceIfMissing("recipes.yml");

        FileConfiguration configYml = loadYaml("config.yml");
        FileConfiguration aspectsYml = loadYaml("aspects.yml");
        FileConfiguration messagesYml = loadYaml("messages.yml");
        FileConfiguration recipesYml = loadYaml("recipes.yml");

        List<String> errors = new ArrayList<>();
        GeneralConfig stagedGeneral = AspectsConfigParser.parseGeneral(configYml, errors);
        Map<String, AspectDefinition> stagedAspects = AspectsConfigParser.parseAspects(aspectsYml, errors);
        RecipesConfig stagedRecipes = AspectsConfigParser.parseRecipes(recipesYml, errors);

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
        this.recipesConfig = stagedRecipes;
    }
```

Add this getter next to the existing `message(String key)` method:
```java
    public RecipesConfig recipes() {
        return recipesConfig;
    }
```

- [ ] **Step 8: Run the full test suite to confirm no regressions**

Run: `mvn -q test`
Expected: 71 tests total (64 before this task, plus these 7 new ones).

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/recipes.yml src/main/java/com/aspects/smp/config/RecipesConfig.java src/main/java/com/aspects/smp/config/AspectsConfigParser.java src/main/java/com/aspects/smp/config/ConfigManager.java src/test/java/com/aspects/smp/config/AspectsConfigParserTest.java
git commit -m "$(cat <<'EOF'
Add RecipesConfig and recipes.yml for the Reroller crafting recipe

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: `CustomItemManager` and `RerollerItem`

**Files:**
- Create: `src/main/java/com/aspects/smp/item/RerollerItem.java`
- Create: `src/main/java/com/aspects/smp/item/CustomItemManager.java`

**Interfaces:**
- Consumes: `MessageUtil.parse` (Phase 1).
- Produces: `CustomItemManager.createReroller() -> ItemStack` and `CustomItemManager.isReroller(ItemStack) -> boolean`, used by Task 5 (`RerollConfirmationGUI`), Task 6 (`RerollerInteractListener`, `RerollerRecipeManager`), and Task 7 (`AspectsPlugin`'s getter).

No test file: this is pure Bukkit-`ItemStack`/`ItemMeta` construction, which requires a running server to even call `new ItemStack(...).getItemMeta()` (Bukkit's item factory is not initialized outside a live server) — manual-verification-only, per this project's established convention for all Bukkit-API-dependent code.

- [ ] **Step 1: Create `src/main/java/com/aspects/smp/item/RerollerItem.java`**

```java
package com.aspects.smp.item;

import java.util.List;

public final class RerollerItem {
    private RerollerItem() {}

    public static final String DISPLAY_NAME = "<gradient:dark_purple:aqua>✦ Aspect Reroller</gradient>";

    public static final List<String> LORE = List.of(
            "<gray>A fragment of unstable elemental energy.</gray>",
            "<gray>Right-click to abandon your current Aspect.</gray>",
            "<gray>A new random Aspect will replace it.</gray>"
    );
}
```

- [ ] **Step 2: Create `src/main/java/com/aspects/smp/item/CustomItemManager.java`**

```java
package com.aspects.smp.item;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.util.MessageUtil;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class CustomItemManager {
    private final NamespacedKey rerollerKey;

    public CustomItemManager(AspectsPlugin plugin) {
        this.rerollerKey = new NamespacedKey(plugin, "reroller");
    }

    public ItemStack createReroller() {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(RerollerItem.DISPLAY_NAME).decoration(TextDecoration.ITALIC, false));
        meta.lore(RerollerItem.LORE.stream()
                .map(line -> MessageUtil.parse(line).decoration(TextDecoration.ITALIC, false))
                .toList());
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(rerollerKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isReroller(ItemStack item) {
        if (item == null || item.getType() != Material.AMETHYST_SHARD || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        Boolean tagged = meta.getPersistentDataContainer().get(rerollerKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(tagged);
    }
}
```

- [ ] **Step 3: Wire a `CustomItemManager` field and getter into `AspectsPlugin` now**

Later tasks in this plan (5 and 6) reference `plugin.getCustomItemManager()`. To keep the project building cleanly after every single task (this project's established convention — every prior batch kept `mvn -q package` green after every task, never leaving a known compile gap across commits), wire the getter in now rather than deferring all `AspectsPlugin` changes to the final task.

In `src/main/java/com/aspects/smp/AspectsPlugin.java`, add this import alongside the existing ones:
```java
import com.aspects.smp.item.CustomItemManager;
```

Add this field next to the existing `private TemporaryEffectManager temporaryEffectManager;`:
```java
    private CustomItemManager customItemManager;
```

Add this line in `onEnable()`, right after the existing `temporaryEffectManager = new TemporaryEffectManager();` line:
```java
        customItemManager = new CustomItemManager(this);
```

Add this getter next to the existing `getTemporaryEffectManager()`:
```java
    public CustomItemManager getCustomItemManager() {
        return customItemManager;
    }
```

(`customItemManager` is constructed but not yet used by anything else — that's expected; Tasks 5 and 6 are what actually call `createReroller()`/`isReroller()`, and Task 7 is what registers the recipe and listeners that put it to use end-to-end.)

- [ ] **Step 4: Build**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`, all existing tests still pass, jar built.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/aspects/smp/item/RerollerItem.java src/main/java/com/aspects/smp/item/CustomItemManager.java src/main/java/com/aspects/smp/AspectsPlugin.java
git commit -m "$(cat <<'EOF'
Add CustomItemManager and RerollerItem for secure item identification

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Extract `AspectManager.performReroll`, add `rerollViaItem`

**Files:**
- Modify: `src/main/java/com/aspects/smp/aspect/AspectManager.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `AspectManager.rerollViaItem(Player player) -> Optional<Aspect>`, used by Task 5's `RerollConfirmationGUI`.

**This is a refactor, not a behavior change.** `adminReroll`'s existing, already-live-tested behavior (exclude current Aspect when configured, persist, clear cooldown and temporary-effect caches, equip new passive) must be bit-for-bit identical after this task. There is no `AspectManagerTest` today, so this is manual-verification-only — the per-task reviewer must line-by-line confirm the extracted `performReroll` body is exactly what `adminReroll`'s body was before this change, just parameterized, and that `adminReroll` itself still produces the exact same `Optional<Aspect>` result and side effects it did before.

- [ ] **Step 1: Read the current `adminReroll` method**

It currently reads (for reference — do not copy this into the new code, it's being replaced):
```java
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
        temporaryEffectManager.clear(player.getUniqueId());
        aspect.passive().onEquip(player, aspect.definition());
        return Optional.of(aspect);
    }
```

- [ ] **Step 2: Replace it with a shared private helper plus two thin public wrappers**

Replace the method above with:
```java
    public Optional<Aspect> adminReroll(Player player) {
        return performReroll(player, excludeCurrentOnReroll);
    }

    public Optional<Aspect> rerollViaItem(Player player) {
        return performReroll(player, excludeCurrentOnReroll);
    }

    private Optional<Aspect> performReroll(Player player, boolean excludeCurrent) {
        Aspect previous = assigned.get(player.getUniqueId());
        String excludeId = excludeCurrent && previous != null ? previous.id() : null;
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
        temporaryEffectManager.clear(player.getUniqueId());
        aspect.passive().onEquip(player, aspect.definition());
        return Optional.of(aspect);
    }
```

(`adminReroll` and `rerollViaItem` currently behave identically — both honor `excludeCurrentOnReroll` — because the original spec does not distinguish item-triggered rerolls from admin-triggered ones for the exclude-current rule. Do not change `AdminCommand`'s call site; `adminReroll(Player)`'s signature and behavior are unchanged.)

- [ ] **Step 3: Build**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`, all existing tests still pass (no new tests in this task), jar built.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/aspects/smp/aspect/AspectManager.java
git commit -m "$(cat <<'EOF'
Extract AspectManager.performReroll, shared by adminReroll and rerollViaItem

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: `AspectsGuiHolder` and `InventoryListener`

**Files:**
- Create: `src/main/java/com/aspects/smp/gui/AspectsGuiHolder.java`
- Create: `src/main/java/com/aspects/smp/listener/InventoryListener.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `AspectsGuiHolder` (interface every GUI class implements), `InventoryListener` (registered once in `AspectsPlugin.onEnable` by Task 7). Task 5's `RerollConfirmationGUI` implements `AspectsGuiHolder`.

No test file: pure Bukkit-event wiring, manual-verification-only.

- [ ] **Step 1: Create `src/main/java/com/aspects/smp/gui/AspectsGuiHolder.java`**

```java
package com.aspects.smp.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

public interface AspectsGuiHolder extends InventoryHolder {
    void onClick(InventoryClickEvent event);

    void onClose(InventoryCloseEvent event);
}
```

- [ ] **Step 2: Create `src/main/java/com/aspects/smp/listener/InventoryListener.java`**

```java
package com.aspects.smp.listener;

import com.aspects.smp.gui.AspectsGuiHolder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public final class InventoryListener implements Listener {
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof AspectsGuiHolder holder) {
            event.setCancelled(true);
            holder.onClick(event);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof AspectsGuiHolder holder) {
            holder.onClose(event);
        }
    }
}
```

(Cancelling the click unconditionally, before any GUI-specific code runs, is what satisfies the spec's "protect GUI inventories against item stealing and shift-click exploits" for every current and future GUI in one place — a GUI class cannot forget to cancel the event, because it never gets the chance not to.)

- [ ] **Step 3: Build**

Run: `mvn -q package -DskipTests`
Expected: exit 0.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/aspects/smp/gui/AspectsGuiHolder.java src/main/java/com/aspects/smp/listener/InventoryListener.java
git commit -m "$(cat <<'EOF'
Add AspectsGuiHolder and InventoryListener shared GUI infrastructure

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: `RerollConfirmationGUI` and its messages

**Files:**
- Create: `src/main/java/com/aspects/smp/gui/RerollConfirmationGUI.java`
- Modify: `src/main/resources/messages.yml`

**Interfaces:**
- Consumes: `AspectsGuiHolder` (Task 4); `CustomItemManager.isReroller` and `AspectsPlugin.getCustomItemManager()` (Task 2); `AspectManager.rerollViaItem` (Task 3).
- Produces: `new RerollConfirmationGUI(AspectsPlugin, Player, int handSlot).open()`, used by Task 6's `RerollerInteractListener`.

No test file: pure Bukkit GUI code, manual-verification-only.

- [ ] **Step 1: Add new message keys to `src/main/resources/messages.yml`**

Change:
```yaml
reroll:
  changed: "<gradient:dark_purple:aqua>Your Aspect has changed!</gradient>"
  previous: "<gray>Previous:</gray> {old}"
  new: "<gray>New Aspect:</gray> {new}"
```
to:
```yaml
reroll:
  changed: "<gradient:dark_purple:aqua>Your Aspect has changed!</gradient>"
  previous: "<gray>Previous:</gray> {old}"
  new: "<gray>New Aspect:</gray> {new}"
  no-eligible-aspect: "<red>No other Aspect is currently available to reroll into.</red>"

gui:
  reroll-confirm:
    title: "<dark_purple>Reroll Aspect?</dark_purple>"
    current-aspect-lore: "<gray>This Aspect will be replaced.</gray>"
    confirm-name: "<green>Confirm Reroll</green>"
    cancel-name: "<red>Cancel</red>"
```

- [ ] **Step 2: Create `src/main/java/com/aspects/smp/gui/RerollConfirmationGUI.java`**

```java
package com.aspects.smp.gui;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.Aspect;
import com.aspects.smp.util.MessageUtil;
import com.aspects.smp.util.ParticleUtil;
import com.aspects.smp.util.SoundUtil;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RerollConfirmationGUI implements AspectsGuiHolder {
    private static final int SIZE = 9;
    private static final int CURRENT_ASPECT_SLOT = 4;
    private static final int CONFIRM_SLOT = 2;
    private static final int CANCEL_SLOT = 6;

    private static final Set<UUID> PROCESSING = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final AspectsPlugin plugin;
    private final Player player;
    private final int handSlot;
    private final Inventory inventory;

    public RerollConfirmationGUI(AspectsPlugin plugin, Player player, int handSlot) {
        this.plugin = plugin;
        this.player = player;
        this.handSlot = handSlot;
        this.inventory = Bukkit.createInventory(this, SIZE,
                MessageUtil.parse(plugin.getConfigManager().message("gui.reroll-confirm.title")));
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
        inventory.setItem(CONFIRM_SLOT,
                namedItem(Material.LIME_WOOL, plugin.getConfigManager().message("gui.reroll-confirm.confirm-name")));
        inventory.setItem(CANCEL_SLOT,
                namedItem(Material.RED_WOOL, plugin.getConfigManager().message("gui.reroll-confirm.cancel-name")));
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
        String name = aspect != null ? aspect.definition().displayName() : "<red>No Aspect</red>";
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MessageUtil.parse(plugin.getConfigManager().message("gui.reroll-confirm.current-aspect-lore"))
                        .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!inventory.equals(event.getClickedInventory())) {
            return;
        }
        int slot = event.getSlot();
        if (slot == CONFIRM_SLOT) {
            handleConfirm();
        } else if (slot == CANCEL_SLOT) {
            player.closeInventory();
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        // Closing without confirming is cancellation; no item was ever consumed, so there is nothing to undo.
    }

    private void handleConfirm() {
        UUID playerId = player.getUniqueId();
        if (!PROCESSING.add(playerId)) {
            return;
        }
        try {
            ItemStack held = player.getInventory().getItem(handSlot);
            if (held == null || !plugin.getCustomItemManager().isReroller(held)) {
                player.closeInventory();
                return;
            }
            Aspect previous = plugin.getAspectManager().getAspect(playerId).orElse(null);
            Optional<Aspect> result = plugin.getAspectManager().rerollViaItem(player);
            if (result.isEmpty()) {
                MessageUtil.sendMessage(player, plugin.getConfigManager().message("reroll.no-eligible-aspect"));
                player.closeInventory();
                return;
            }

            held.subtract(1);
            player.getInventory().setItem(handSlot, held.getAmount() <= 0 ? null : held);

            Aspect newAspect = result.get();
            MessageUtil.sendMessage(player, plugin.getConfigManager().message("reroll.changed"));
            if (previous != null) {
                MessageUtil.sendMessage(player, plugin.getConfigManager().message("reroll.previous"),
                        Map.of("old", previous.definition().displayName()));
            }
            MessageUtil.sendMessage(player, plugin.getConfigManager().message("reroll.new"),
                    Map.of("new", newAspect.definition().displayName()));
            SoundUtil.play(player, "entity.player.levelup", 1.0f, 1.0f, plugin.getLogger());
            ParticleUtil.spawn(player.getLocation(), "TOTEM_OF_UNDYING", 20, plugin.getLogger());
            player.closeInventory();
        } finally {
            PROCESSING.remove(playerId);
        }
    }
}
```

- [ ] **Step 3: Build**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`, all existing tests still pass, jar built.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/messages.yml src/main/java/com/aspects/smp/gui/RerollConfirmationGUI.java
git commit -m "$(cat <<'EOF'
Add RerollConfirmationGUI

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: `RerollerInteractListener` and `RerollerRecipeManager`

**Files:**
- Create: `src/main/java/com/aspects/smp/item/RerollerInteractListener.java`
- Create: `src/main/java/com/aspects/smp/recipe/RerollerRecipeManager.java`

**Interfaces:**
- Consumes: `CustomItemManager.isReroller`/`createReroller` and `AspectsPlugin.getCustomItemManager()` (Task 2); `RecipesConfig` (Task 1); `RerollConfirmationGUI` (Task 5).
- Produces: `RerollerInteractListener` (registered in Task 7's `onEnable`), `RerollerRecipeManager.register(RecipesConfig)` (called in Task 7's `onEnable`).

No test file: pure Bukkit event/recipe code, manual-verification-only.

- [ ] **Step 1: Create `src/main/java/com/aspects/smp/item/RerollerInteractListener.java`**

```java
package com.aspects.smp.item;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.gui.RerollConfirmationGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class RerollerInteractListener implements Listener {
    private final AspectsPlugin plugin;

    public RerollerInteractListener(AspectsPlugin plugin) {
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
        ItemStack item = event.getItem();
        if (!plugin.getCustomItemManager().isReroller(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        int handSlot = player.getInventory().getHeldItemSlot();
        new RerollConfirmationGUI(plugin, player, handSlot).open();
    }
}
```

- [ ] **Step 2: Create `src/main/java/com/aspects/smp/recipe/RerollerRecipeManager.java`**

```java
package com.aspects.smp.recipe;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.config.RecipesConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

import java.util.Map;

public final class RerollerRecipeManager {
    private static final String RECIPE_KEY = "reroller_recipe";

    private final AspectsPlugin plugin;

    public RerollerRecipeManager(AspectsPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(RecipesConfig config) {
        NamespacedKey key = new NamespacedKey(plugin, RECIPE_KEY);
        Bukkit.removeRecipe(key);
        if (!config.rerollerEnabled()) {
            return;
        }
        ItemStack result = plugin.getCustomItemManager().createReroller();
        result.setAmount(config.rerollerResultAmount());
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(config.rerollerPattern().toArray(new String[0]));
        for (Map.Entry<Character, Material> entry : config.rerollerIngredients().entrySet()) {
            recipe.setIngredient(entry.getKey(), entry.getValue());
        }
        Bukkit.addRecipe(recipe);
    }
}
```

- [ ] **Step 3: Build**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`, all existing tests still pass, jar built.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/aspects/smp/item/RerollerInteractListener.java src/main/java/com/aspects/smp/recipe/RerollerRecipeManager.java
git commit -m "$(cat <<'EOF'
Add RerollerInteractListener and RerollerRecipeManager

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Wire everything into `AspectsPlugin`, full build

**Files:**
- Modify: `src/main/java/com/aspects/smp/AspectsPlugin.java`

**Interfaces:**
- Consumes: everything from Tasks 1–6.
- Produces: nothing new for later tasks — this is the final integration point for Batch 3a.

- [ ] **Step 1: Read the current `src/main/java/com/aspects/smp/AspectsPlugin.java`**

(It should already have the `CustomItemManager` field/getter from Task 2, the `TemporaryEffectManager` field/getter and `PlayerDeathListener` registration from Batch B2b, and 8 entries each in `PASSIVE_FACTORIES`/`ACTIVE_FACTORIES`. `RerollerInteractListener`, `InventoryListener`, and `RerollerRecipeManager` should NOT be referenced yet. If it looks different from what this plan expects, stop and report BLOCKED rather than guessing.)

- [ ] **Step 2: Add imports**

Add these imports alongside the existing ones, in alphabetical position:
```java
import com.aspects.smp.item.RerollerInteractListener;
import com.aspects.smp.listener.InventoryListener;
import com.aspects.smp.recipe.RerollerRecipeManager;
```

- [ ] **Step 3: Add a field**

Add this field next to the existing `private CustomItemManager customItemManager;`:
```java
    private RerollerRecipeManager rerollerRecipeManager;
```

- [ ] **Step 4: Construct and wire in `onEnable()`**

Add, right after the existing `customItemManager = new CustomItemManager(this);` line:
```java
        rerollerRecipeManager = new RerollerRecipeManager(this);
```

Add these two listener registrations to the existing block of `getServer().getPluginManager().registerEvents(...)` calls (anywhere in that block; appending at the end is simplest):
```java
        getServer().getPluginManager().registerEvents(new InventoryListener(), this);
        getServer().getPluginManager().registerEvents(new RerollerInteractListener(this), this);
```

Add the recipe registration call right after the existing `for (Player player : getServer().getOnlinePlayers()) { aspectManager.loadOrAssign(player, () -> {}); }` loop, before the final `getLogger().info(...)` line:
```java
        rerollerRecipeManager.register(configManager.recipes());
```

- [ ] **Step 5: Full build and test**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`, all 71 unit tests pass, and `target/aspects-1.0.0-SNAPSHOT.jar` exists.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/aspects/smp/AspectsPlugin.java
git commit -m "$(cat <<'EOF'
Wire the Reroller item, recipe, and confirmation GUI into AspectsPlugin

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 7: Note for the controller, not this task's implementer**

Live verification against a running Paper 1.21.11 server is the controller's responsibility after this task's review passes, the same way every prior batch's final wiring task was handled — do not attempt this as the task implementer; there is no running server in that environment. Specifically verify: the crafting recipe produces a correctly-tagged, correctly-named/lored Reroller item (not a plain amethyst shard); crafting with a plain amethyst shard substituted for the Nether Star fails to produce a Reroller; right-clicking the Reroller opens the confirmation GUI; clicking Confirm consumes exactly one Reroller and assigns a new Aspect (excluding the previous one, per `exclude-current-aspect: true`); clicking Cancel or closing the GUI consumes nothing and changes nothing; renaming a plain amethyst shard to "✦ Aspect Reroller" via an anvil does NOT make it function as a Reroller (confirms PDC-only identification).
