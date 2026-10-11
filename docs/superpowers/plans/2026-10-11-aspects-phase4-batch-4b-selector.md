# Aspects Plugin — Phase 4 Batch 4b (Aspect Selector Item) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the two Aspect Selector concepts from the original spec: `/aspects admin give <player>
<aspect>` (a token for one specific, named Aspect) and `/aspects admin give-selection <player>` (a generic
selector that opens a picker GUI). Using either item never silently assigns an Aspect from the command side —
assignment only happens when the recipient actually uses the item, through a confirmation flow.

**Architecture:** `CustomItemManager` (Phase 3 Batch 3a) gains two new PDC-tagged item types (a
`STRING`-valued specific-Aspect token and a `BOOLEAN`-valued generic selector), each on its own distinct
`Material` so neither can be confused with the Reroller or with each other. `AspectManager` gains a fourth
thin wrapper, `assignViaToken`, delegating straight to the existing `adminSet` logic. Two new, self-contained
GUI classes (`TokenConfirmationGUI`, `AspectSelectorGUI`) mirror `RerollConfirmationGUI`/`AspectBrowserGUI`'s
existing structure — see the design doc's revised decision to duplicate rather than generalize the existing
confirmation GUI. One new listener, `AspectSelectorInteractListener`, handles right-click for both item
types and independently checks the *holder's* permission before acting — never trusting that merely
possessing the item proves authorization, per the spec's explicit requirement.

**Tech Stack:** Java 21, Paper API `1.21.11-R0.1-SNAPSHOT`, Maven, JUnit 5.

## Global Constraints

- Every commit ends with exactly: `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>` — regardless of which model generates it. This is a fixed project convention; implementers must not substitute their own model name.
- An item is identified **only** via its `PersistentDataContainer` tag, exactly like the Reroller (Phase 3
  Batch 3a) — never by name or lore.
- Using a Selector item must never consume it or assign anything without the recipient's own confirming
  click, per the spec's explicit "do not silently assign the Aspect when the command claims to give an
  item" — the admin command only ever hands over an item; the item's *use* is what performs the assignment.
- The interact listener checks the permission of the player *holding and using* the item
  (`aspects.admin.give` for the specific token, `aspects.admin.selector` for the generic selector) — not
  just whoever ran the admin command that originally gave it out — per the spec's "never trust an item
  displayed/held as proof of authorization."
- Bukkit-API-dependent code (`ItemStack`, `ItemMeta`, `Inventory`, GUI classes, command handlers touching
  `Player`/`CommandSender`) is manual-verification-only, matching every such class in this project.
- Build/test command, used identically in every task:
  ```
  export JAVA_HOME="/home/erikkalstrom/.local/share/flatpak/runtime/org.freedesktop.Sdk.Extension.openjdk21/x86_64/26.08/d4d21c831d4b2abcc5c2c1f32d7ac4a5cac886db0328a94a259021e26e93689a/files/jvm/openjdk-21"
  export PATH="$JAVA_HOME/bin:/tmp/apache-maven-3.9.9/bin:$PATH"
  ```
- Starting test count before this plan: 84. No task in this batch is expected to add tests — everything new
  here is either Bukkit-API-dependent or a trivial delegating wrapper with no branching logic to unit-test
  (consistent with `AspectManager.rerollViaItem`/`adminSet` having no dedicated tests today either).
- The Admin GUI (Batch 4c) is explicitly out of scope here.

---

### Task 1: `CustomItemManager` gains Selector item support

**Files:**
- Create: `src/main/java/com/aspects/smp/item/AspectSelectorItem.java`
- Modify: `src/main/java/com/aspects/smp/item/CustomItemManager.java`

**Interfaces:**
- Consumes: `AspectsPlugin.getConfigManager().aspectDefinitions()` (Phase 1), for looking up a token's target
  Aspect's display name for its lore.
- Produces: `CustomItemManager.createSelectorToken(String aspectId) -> ItemStack`,
  `getSelectorTokenAspectId(ItemStack) -> Optional<String>`, `createGenericSelector() -> ItemStack`,
  `isGenericSelector(ItemStack) -> boolean`. Used by Task 3 (`TokenConfirmationGUI`), Task 4
  (`AspectSelectorGUI`), Task 5 (`AspectSelectorInteractListener`, `AdminCommand`).

No test file: pure Bukkit `ItemStack`/`ItemMeta` construction, manual-verification-only, matching
`CustomItemManager`'s existing Reroller methods from Phase 3.

- [ ] **Step 1: Create `src/main/java/com/aspects/smp/item/AspectSelectorItem.java`**

```java
package com.aspects.smp.item;

import java.util.List;

public final class AspectSelectorItem {
    private AspectSelectorItem() {}

    public static final String TOKEN_DISPLAY_NAME = "<gradient:dark_purple:aqua>✦ Aspect Selection Token</gradient>";

    public static List<String> tokenLore(String targetAspectDisplayName) {
        return List.of(
                "<gray>A token imbued with a specific elemental power.</gray>",
                "<gray>Right-click to become " + targetAspectDisplayName + ".</gray>",
                "<gray>This will replace your current Aspect.</gray>"
        );
    }

    public static final String SELECTOR_DISPLAY_NAME = "<gradient:dark_purple:aqua>✦ Aspect Selector</gradient>";

    public static final List<String> SELECTOR_LORE = List.of(
            "<gray>A compass that points toward your true power.</gray>",
            "<gray>Right-click to choose a new Aspect.</gray>",
            "<gray>This will replace your current Aspect.</gray>"
    );
}
```

- [ ] **Step 2: Replace the full contents of `CustomItemManager.java`**

```java
package com.aspects.smp.item;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.util.MessageUtil;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

public final class CustomItemManager {
    private final AspectsPlugin plugin;
    private final NamespacedKey rerollerKey;
    private final NamespacedKey selectorSpecificKey;
    private final NamespacedKey selectorGenericKey;

    public CustomItemManager(AspectsPlugin plugin) {
        this.plugin = plugin;
        this.rerollerKey = new NamespacedKey(plugin, "reroller");
        this.selectorSpecificKey = new NamespacedKey(plugin, "selector-specific");
        this.selectorGenericKey = new NamespacedKey(plugin, "selector-generic");
    }

    public ItemStack createReroller() {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(RerollerItem.DISPLAY_NAME).decoration(TextDecoration.ITALIC, false));
        meta.lore(RerollerItem.LORE.stream()
                .map(line -> MessageUtil.parse(line).decoration(TextDecoration.ITALIC, false))
                .toList());
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

    public ItemStack createSelectorToken(String aspectId) {
        AspectDefinition definition = plugin.getConfigManager().aspectDefinitions().get(aspectId);
        String targetDisplayName = definition != null ? definition.displayName() : aspectId;

        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(AspectSelectorItem.TOKEN_DISPLAY_NAME).decoration(TextDecoration.ITALIC, false));
        meta.lore(AspectSelectorItem.tokenLore(targetDisplayName).stream()
                .map(line -> MessageUtil.parse(line).decoration(TextDecoration.ITALIC, false))
                .toList());
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(selectorSpecificKey, PersistentDataType.STRING, aspectId);
        item.setItemMeta(meta);
        return item;
    }

    public Optional<String> getSelectorTokenAspectId(ItemStack item) {
        if (item == null || item.getType() != Material.ENDER_EYE || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String aspectId = item.getItemMeta().getPersistentDataContainer().get(selectorSpecificKey, PersistentDataType.STRING);
        return Optional.ofNullable(aspectId);
    }

    public ItemStack createGenericSelector() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(AspectSelectorItem.SELECTOR_DISPLAY_NAME).decoration(TextDecoration.ITALIC, false));
        meta.lore(AspectSelectorItem.SELECTOR_LORE.stream()
                .map(line -> MessageUtil.parse(line).decoration(TextDecoration.ITALIC, false))
                .toList());
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(selectorGenericKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isGenericSelector(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) {
            return false;
        }
        Boolean tagged = item.getItemMeta().getPersistentDataContainer().get(selectorGenericKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(tagged);
    }
}
```

(`createReroller`/`isReroller` are unchanged from before this task — only the new fields, the new
constructor body storing `plugin`, and the four new methods are added.)

- [ ] **Step 3: Build**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`, all 84 tests pass, jar built.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/aspects/smp/item/AspectSelectorItem.java src/main/java/com/aspects/smp/item/CustomItemManager.java
git commit -m "$(cat <<'EOF'
Add Aspect Selector item identification to CustomItemManager

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: `AspectManager.assignViaToken`

**Files:**
- Modify: `src/main/java/com/aspects/smp/aspect/AspectManager.java`

**Interfaces:**
- Consumes: the existing `adminSet` method (Phase 1).
- Produces: `AspectManager.assignViaToken(Player player, String aspectId) -> Optional<Aspect>`, used by
  Task 3's `TokenConfirmationGUI` and Task 4's `AspectSelectorGUI`.

- [ ] **Step 1: Add the method**

Add this public method right after the existing `resetAspect` method (added in Batch 4a):
```java
    public Optional<Aspect> assignViaToken(Player player, String aspectId) {
        return adminSet(player, aspectId);
    }
```
(This is a pure delegate with no new logic — `assignViaToken` exists only so call sites in item-consumption
GUIs read clearly as "a player is choosing/consuming a token to become this Aspect," distinct from "an admin
is directly commanding a reassignment" at `adminSet`'s own call sites. Both share the exact same validation
and side effects: unknown/disabled Aspect id returns empty, otherwise unequip the old passive, persist,
clear cooldown/temporary-effect caches, equip the new passive.)

- [ ] **Step 2: Build**

Run: `mvn -q package -DskipTests`
Expected: exit 0.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/aspects/smp/aspect/AspectManager.java
git commit -m "$(cat <<'EOF'
Add AspectManager.assignViaToken

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: `TokenConfirmationGUI`

**Files:**
- Create: `src/main/java/com/aspects/smp/gui/TokenConfirmationGUI.java`
- Modify: `src/main/resources/messages.yml`

**Interfaces:**
- Consumes: `AspectsGuiHolder` (Phase 3 Batch 3a); `CustomItemManager.getSelectorTokenAspectId` (Task 1);
  `AspectManager.assignViaToken` (Task 2); `AspectRegistry.get` (Phase 1, via `AspectsPlugin.getAspectRegistry()`).
- Produces: `new TokenConfirmationGUI(AspectsPlugin, Player, int handSlot, String targetAspectId).open()`,
  used by Task 5's `AspectSelectorInteractListener`.

A confirmation GUI for "become this one specific Aspect," mirroring `RerollConfirmationGUI`'s structure
exactly (per the design doc's revised decision to duplicate rather than share a base class) but confirming a
specific, pre-chosen Aspect instead of a random reroll.

No test file: pure Bukkit GUI code, manual-verification-only.

- [ ] **Step 1: Add new message keys to `src/main/resources/messages.yml`**

Add this new section inside the existing `gui:` section, as a sibling of `reroll-confirm:`, `details:`,
`browser:`, and `main:`:
```yaml
  token-confirm:
    title: "<dark_purple>Become this Aspect?</dark_purple>"
    target-aspect-lore: "<gray>This will replace your current Aspect.</gray>"
    confirm-name: "<green>Confirm</green>"
    cancel-name: "<red>Cancel</red>"
```

- [ ] **Step 2: Create `src/main/java/com/aspects/smp/gui/TokenConfirmationGUI.java`**

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

public final class TokenConfirmationGUI implements AspectsGuiHolder {
    private static final int SIZE = 9;
    private static final int TARGET_ASPECT_SLOT = 4;
    private static final int CONFIRM_SLOT = 2;
    private static final int CANCEL_SLOT = 6;

    private static final Set<UUID> PROCESSING = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final AspectsPlugin plugin;
    private final Player player;
    private final int handSlot;
    private final String targetAspectId;
    private final Inventory inventory;

    public TokenConfirmationGUI(AspectsPlugin plugin, Player player, int handSlot, String targetAspectId) {
        this.plugin = plugin;
        this.player = player;
        this.handSlot = handSlot;
        this.targetAspectId = targetAspectId;
        this.inventory = Bukkit.createInventory(this, SIZE,
                MessageUtil.parse(plugin.getConfigManager().message("gui.token-confirm.title")));
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
        inventory.setItem(TARGET_ASPECT_SLOT, targetAspectItem());
        inventory.setItem(CONFIRM_SLOT,
                namedItem(Material.LIME_WOOL, plugin.getConfigManager().message("gui.token-confirm.confirm-name")));
        inventory.setItem(CANCEL_SLOT,
                namedItem(Material.RED_WOOL, plugin.getConfigManager().message("gui.token-confirm.cancel-name")));
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(name).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack targetAspectItem() {
        Aspect target = plugin.getAspectRegistry().get(targetAspectId).orElse(null);
        Material icon = target != null ? target.definition().icon() : Material.BARRIER;
        String name = target != null ? target.definition().displayName() : "<red>Unknown Aspect</red>";
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MessageUtil.parse(plugin.getConfigManager().message("gui.token-confirm.target-aspect-lore"))
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
            Optional<String> heldAspectId = held == null
                    ? Optional.empty()
                    : plugin.getCustomItemManager().getSelectorTokenAspectId(held);
            if (heldAspectId.isEmpty() || !heldAspectId.get().equals(targetAspectId)) {
                player.closeInventory();
                return;
            }
            Aspect previous = plugin.getAspectManager().getAspect(playerId).orElse(null);
            Optional<Aspect> result = plugin.getAspectManager().assignViaToken(player, targetAspectId);
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
            if (plugin.getConfigManager().general().revealAnimationEnabled()) {
                SoundUtil.play(player, "entity.player.levelup", 1.0f, 1.0f, plugin.getLogger());
                ParticleUtil.spawn(player.getLocation(), "TOTEM_OF_UNDYING", 20, plugin.getLogger());
            }
            player.closeInventory();
        } finally {
            PROCESSING.remove(playerId);
        }
    }
}
```

- [ ] **Step 3: Build**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`, all 84 tests pass, jar built.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/messages.yml src/main/java/com/aspects/smp/gui/TokenConfirmationGUI.java
git commit -m "$(cat <<'EOF'
Add TokenConfirmationGUI for specific-Aspect selection tokens

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: `AspectSelectorGUI`

**Files:**
- Create: `src/main/java/com/aspects/smp/gui/AspectSelectorGUI.java`
- Modify: `src/main/resources/messages.yml`

**Interfaces:**
- Consumes: `AspectsGuiHolder` (Phase 3 Batch 3a); `AspectRegistry.enabled()` (Phase 1/3);
  `CustomItemManager.isGenericSelector` (Task 1); `AspectManager.assignViaToken` (Task 2).
- Produces: `new AspectSelectorGUI(AspectsPlugin, Player, int handSlot).open()`, used by Task 5's
  `AspectSelectorInteractListener`.

A picker GUI listing every enabled Aspect (visually like `AspectBrowserGUI`), but clicking an entry *assigns*
that Aspect and consumes the held generic selector — unlike `AspectBrowserGUI`, which is read-only.

No test file: pure Bukkit GUI code, manual-verification-only.

- [ ] **Step 1: Add new message keys to `src/main/resources/messages.yml`**

Add this new section inside the existing `gui:` section, as a sibling of the other `gui.*` sections:
```yaml
  selector:
    title: "<dark_purple>Choose Your Aspect</dark_purple>"
    choose-lore: "<gray>Click to become this Aspect.</gray>"
    cancel-name: "<red>Cancel</red>"
```

- [ ] **Step 2: Create `src/main/java/com/aspects/smp/gui/AspectSelectorGUI.java`**

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AspectSelectorGUI implements AspectsGuiHolder {
    private static final int SIZE = 54;
    private static final int CANCEL_SLOT = 49;

    private static final Set<UUID> PROCESSING = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final AspectsPlugin plugin;
    private final Player player;
    private final int handSlot;
    private final Inventory inventory;
    private final Map<Integer, Aspect> aspectBySlot = new HashMap<>();

    public AspectSelectorGUI(AspectsPlugin plugin, Player player, int handSlot) {
        this.plugin = plugin;
        this.player = player;
        this.handSlot = handSlot;
        this.inventory = Bukkit.createInventory(this, SIZE,
                MessageUtil.parse(plugin.getConfigManager().message("gui.selector.title")));
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
            if (slot >= CANCEL_SLOT) {
                break;
            }
            inventory.setItem(slot, selectorItem(aspect));
            aspectBySlot.put(slot, aspect);
            slot++;
        }

        inventory.setItem(CANCEL_SLOT, namedItem(Material.RED_WOOL, plugin.getConfigManager().message("gui.selector.cancel-name")));
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(name).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack selectorItem(Aspect aspect) {
        ItemStack item = new ItemStack(aspect.definition().icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(aspect.definition().displayName()).decoration(TextDecoration.ITALIC, false));
        List<String> lore = List.of(
                "<" + aspect.definition().rarity().color() + ">" + aspect.definition().rarity().name() + "</" +
                        aspect.definition().rarity().color() + ">",
                aspect.definition().description(),
                " ",
                plugin.getConfigManager().message("gui.selector.choose-lore"));
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
        if (slot == CANCEL_SLOT) {
            player.closeInventory();
            return;
        }
        Aspect clicked = aspectBySlot.get(slot);
        if (clicked != null) {
            handleChoice(clicked);
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        // Closing without choosing is cancellation; no item was ever consumed, so there is nothing to undo.
    }

    private void handleChoice(Aspect chosen) {
        UUID playerId = player.getUniqueId();
        if (!PROCESSING.add(playerId)) {
            return;
        }
        try {
            ItemStack held = player.getInventory().getItem(handSlot);
            if (held == null || !plugin.getCustomItemManager().isGenericSelector(held)) {
                player.closeInventory();
                return;
            }
            Aspect previous = plugin.getAspectManager().getAspect(playerId).orElse(null);
            Optional<Aspect> result = plugin.getAspectManager().assignViaToken(player, chosen.id());
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
            if (plugin.getConfigManager().general().revealAnimationEnabled()) {
                SoundUtil.play(player, "entity.player.levelup", 1.0f, 1.0f, plugin.getLogger());
                ParticleUtil.spawn(player.getLocation(), "TOTEM_OF_UNDYING", 20, plugin.getLogger());
            }
            player.closeInventory();
        } finally {
            PROCESSING.remove(playerId);
        }
    }
}
```

- [ ] **Step 3: Build**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`, all 84 tests pass, jar built.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/messages.yml src/main/java/com/aspects/smp/gui/AspectSelectorGUI.java
git commit -m "$(cat <<'EOF'
Add AspectSelectorGUI for the generic Aspect Selector item

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: `AspectSelectorInteractListener`, `AdminCommand` `give`/`give-selection`, permission, tab completion

**Files:**
- Create: `src/main/java/com/aspects/smp/item/AspectSelectorInteractListener.java`
- Modify: `src/main/java/com/aspects/smp/command/AdminCommand.java`
- Modify: `src/main/java/com/aspects/smp/command/AspectsTabCompleter.java`
- Modify: `src/main/resources/plugin.yml`

**Interfaces:**
- Consumes: `CustomItemManager.getSelectorTokenAspectId`/`isGenericSelector`/`createSelectorToken`/
  `createGenericSelector` (Task 1); `TokenConfirmationGUI` (Task 3); `AspectSelectorGUI` (Task 4);
  `AspectRegistry.get` (Phase 1, via `AspectsPlugin.getAspectRegistry()`).
- Produces: `AspectSelectorInteractListener` (registered in Task 6's `onEnable`); two new `AdminCommand`
  subcommands.

No test file for the listener or command handlers (pure Bukkit event/command code, manual-verification-only,
matching `RerollerInteractListener`/`AdminCommand`'s other subcommands).

- [ ] **Step 1: Create `src/main/java/com/aspects/smp/item/AspectSelectorInteractListener.java`**

```java
package com.aspects.smp.item;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.gui.AspectSelectorGUI;
import com.aspects.smp.gui.TokenConfirmationGUI;
import com.aspects.smp.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public final class AspectSelectorInteractListener implements Listener {
    private final AspectsPlugin plugin;

    public AspectSelectorInteractListener(AspectsPlugin plugin) {
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
        Player player = event.getPlayer();

        Optional<String> tokenAspectId = plugin.getCustomItemManager().getSelectorTokenAspectId(item);
        if (tokenAspectId.isPresent()) {
            event.setCancelled(true);
            if (!player.hasPermission("aspects.admin.give")) {
                player.sendMessage(MessageUtil.parse("<red>You do not have permission to use this item.</red>"));
                return;
            }
            int handSlot = player.getInventory().getHeldItemSlot();
            new TokenConfirmationGUI(plugin, player, handSlot, tokenAspectId.get()).open();
            return;
        }

        if (plugin.getCustomItemManager().isGenericSelector(item)) {
            event.setCancelled(true);
            if (!player.hasPermission("aspects.admin.selector")) {
                player.sendMessage(MessageUtil.parse("<red>You do not have permission to use this item.</red>"));
                return;
            }
            int handSlot = player.getInventory().getHeldItemSlot();
            new AspectSelectorGUI(plugin, player, handSlot).open();
        }
    }
}
```

- [ ] **Step 2: Add `give` and `give-selection` to `AdminCommand`**

No new imports are needed in `src/main/java/com/aspects/smp/command/AdminCommand.java` for this step —
`Aspect`, `ItemStack`, `Map`, `Player`, `CommandSender`, and `Bukkit` are all already imported (from the
existing `set`/`reroll`/`give-reroller` work). Confirm this before proceeding; if any are missing, something
doesn't match this plan's assumptions and you should stop and report BLOCKED rather than guessing.

Change the `switch` in `handle`:
```java
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
```
to:
```java
        return switch (sub) {
            case "set" -> handleSet(sender, args);
            case "reroll" -> handleReroll(sender, args);
            case "reset" -> handleReset(sender, args);
            case "give-reroller" -> handleGiveReroller(sender, args);
            case "give" -> handleGive(sender, args);
            case "give-selection" -> handleGiveSelection(sender, args);
            case "clear-cooldown" -> handleClearCooldown(sender, args);
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender);
            case "debug" -> handleDebug(sender, args);
            default -> {
                sender.sendMessage(MessageUtil.parse("<red>Unknown admin subcommand '" + sub + "'.</red>"));
                yield true;
            }
        };
```

Also update the no-args usage line right above the `switch`:
```java
            sender.sendMessage(MessageUtil.parse(
                    "<red>Usage: /aspects admin <set|reroll|reset|give-reroller|clear-cooldown|reload|list|debug> ...</red>"));
```
to:
```java
            sender.sendMessage(MessageUtil.parse(
                    "<red>Usage: /aspects admin <set|reroll|reset|give-reroller|give|give-selection|clear-cooldown|reload|list|debug> ...</red>"));
```

Add these two new methods right after the existing `handleGiveReroller`/`clampGiveAmount` methods:
```java
    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aspects.admin.give")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse("<red>Usage: /aspects admin give <player> <aspect></red>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse("<red>Player '" + args[1] + "' is not online.</red>"));
            return true;
        }
        String aspectId = args[2].toLowerCase();
        if (plugin.getAspectRegistry().get(aspectId).isEmpty()) {
            sender.sendMessage(MessageUtil.parse("<red>Unknown Aspect id: " + aspectId + "</red>"));
            return true;
        }
        ItemStack token = plugin.getCustomItemManager().createSelectorToken(aspectId);
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(token);
        if (!leftover.isEmpty()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover.values().iterator().next());
            sender.sendMessage(MessageUtil.parse("<yellow>" + target.getName() +
                    "'s inventory was full — the token was dropped at their feet.</yellow>"));
        }
        plugin.getLogger().info(sender.getName() + " gave " + target.getName() + " an Aspect Selection Token for " + aspectId);
        sender.sendMessage(MessageUtil.parse("<green>Gave " + target.getName() + " a Selection Token for " + aspectId + ".</green>"));
        return true;
    }

    private boolean handleGiveSelection(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aspects.admin.selector")) {
            sender.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>Usage: /aspects admin give-selection <player></red>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse("<red>Player '" + args[1] + "' is not online.</red>"));
            return true;
        }
        ItemStack selector = plugin.getCustomItemManager().createGenericSelector();
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(selector);
        if (!leftover.isEmpty()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover.values().iterator().next());
            sender.sendMessage(MessageUtil.parse("<yellow>" + target.getName() +
                    "'s inventory was full — the Selector was dropped at their feet.</yellow>"));
        }
        plugin.getLogger().info(sender.getName() + " gave " + target.getName() + " an Aspect Selector");
        sender.sendMessage(MessageUtil.parse("<green>Gave " + target.getName() + " an Aspect Selector.</green>"));
        return true;
    }
```

- [ ] **Step 3: Add the `aspects.admin.selector` permission to `plugin.yml`**

Add this entry to the `permissions:` section, after the existing `aspects.admin.give` entry:
```yaml
  aspects.admin.selector:
    description: Give a generic Aspect Selector item that lets a player choose their own Aspect.
    default: op
```

- [ ] **Step 4: Extend `AspectsTabCompleter`**

In `src/main/java/com/aspects/smp/command/AspectsTabCompleter.java`, change:
```java
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
```
to:
```java
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            options.add("set");
            options.add("reroll");
            options.add("reset");
            options.add("give-reroller");
            options.add("give");
            options.add("give-selection");
            options.add("clear-cooldown");
            options.add("reload");
            options.add("list");
            options.add("debug");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin")
                && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("reroll")
                        || args[1].equalsIgnoreCase("reset") || args[1].equalsIgnoreCase("give-reroller")
                        || args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("give-selection")
                        || args[1].equalsIgnoreCase("clear-cooldown") || args[1].equalsIgnoreCase("debug"))) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                options.add(player.getName());
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("admin")
                && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("give"))) {
            options.addAll(plugin.getConfigManager().aspectDefinitions().keySet());
        }
```

- [ ] **Step 5: Build and test**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`, all 84 tests pass, jar built.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/aspects/smp/item/AspectSelectorInteractListener.java src/main/java/com/aspects/smp/command/AdminCommand.java src/main/java/com/aspects/smp/command/AspectsTabCompleter.java src/main/resources/plugin.yml
git commit -m "$(cat <<'EOF'
Add admin give/give-selection commands and the Selector interact listener

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Wire `AspectSelectorInteractListener` into `AspectsPlugin`, full build

**Files:**
- Modify: `src/main/java/com/aspects/smp/AspectsPlugin.java`

**Interfaces:**
- Consumes: `AspectSelectorInteractListener` (Task 5).
- Produces: nothing new for later tasks — this is the final integration point for Batch 4b.

- [ ] **Step 1: Read the current `src/main/java/com/aspects/smp/AspectsPlugin.java`**

(It should already have `RerollerInteractListener`/`RerollerProtectionListener` registered from Phase 3
Batch 3a, and nothing referencing `AspectSelectorInteractListener` yet. If it looks different from what this
plan expects, stop and report BLOCKED rather than guessing.)

- [ ] **Step 2: Add the import**

Add this import alongside the existing `com.aspects.smp.item.*` imports:
```java
import com.aspects.smp.item.AspectSelectorInteractListener;
```

- [ ] **Step 3: Register the listener in `onEnable()`**

Add this line right after the existing `getServer().getPluginManager().registerEvents(new RerollerProtectionListener(this), this);` line:
```java
        getServer().getPluginManager().registerEvents(new AspectSelectorInteractListener(this), this);
```

- [ ] **Step 4: Full build and test**

Run: `mvn -q package`
Expected: `BUILD SUCCESS`, all 84 tests pass, jar built.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/aspects/smp/AspectsPlugin.java
git commit -m "$(cat <<'EOF'
Wire AspectSelectorInteractListener into AspectsPlugin

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 6: Note for the controller, not this task's implementer**

Live verification against a running Paper 1.21.11 server is the controller's responsibility after this
task's review passes — do not attempt this as the task implementer; there is no running server in that
environment. Specifically verify: `/aspects admin give <player> <aspect>` delivers a correctly-tagged
Selection Token whose lore names the target Aspect; right-clicking it opens `TokenConfirmationGUI` showing
that specific Aspect; Confirm consumes it and assigns exactly that Aspect (not a random one); Cancel/close
consumes nothing; `/aspects admin give-selection <player>` delivers a correctly-tagged generic Selector;
right-clicking it opens `AspectSelectorGUI` listing all 8 enabled Aspects; clicking one consumes the Selector
and assigns that Aspect; a player who is NOT holding the permission (if a permissions plugin is available to
test with) cannot use either item even while holding it, confirming the holder-side permission check in
`AspectSelectorInteractListener`; a renamed, untagged item of the same material does not function as either
Selector variant.
