# Aspects Plugin — Phase 4 Batch 4c (Administrative GUI) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the administrative GUI (`/aspects admin` with no further args) that lists online players,
and per-player sub-menus to assign a specific Aspect, force a reroll (with confirmation), give a Reroller,
and clear cooldowns — all re-checking their own permission node at the moment the action executes, per the
design doc's explicit "never trust an item or an open GUI as proof of authorization" requirement.

**Architecture:** Four new `gui` classes, built bottom-up so each later class's dependencies already exist:
`AdminAspectAssignGUI` and `AdminRerollConfirmGUI` (the two leaf action GUIs, independent of each other),
then `AdminPlayerMenuGUI` (the per-player sub-menu that opens both leaf GUIs and performs the two
immediate actions directly), then `AdminGUI` (the top-level player-list menu, wired into
`AdminCommand.handle` for the no-args case). All four implement the existing `AspectsGuiHolder` interface
and are handled automatically by the existing, unmodified `listener.InventoryListener` — no listener changes
are needed. Every GUI follows this codebase's established per-class pattern (duplicated small
`namedItem`/filler helpers, no shared abstract base — see Phase 4 Batch 4b's design doc for why this
project deliberately does not generalize GUI classes) and reuses `AspectManager.adminSet`/`adminReroll` and
`AbilityCooldownManager.clearCache` exactly as `AdminCommand`'s existing text-command handlers already do —
this batch adds no new manager/business logic, only a UI layer over operations that already exist and are
already tested via `AdminCommand`'s own manual-verification path.

**Tech Stack:** Paper/Bukkit 1.21 API (`Inventory`, `ItemStack`, `SkullMeta`), Kyori Adventure (`MessageUtil`),
no new dependencies.

## Global Constraints

- Every destructive or state-changing action (assign, force-reroll, give-reroller, clear-cooldown) re-checks
  its own specific permission node (`aspects.admin.set`, `aspects.admin.reroll`, `aspects.admin.give`,
  `aspects.admin.cooldown`) on the **viewing player**, at the exact moment that action executes — never only
  once at GUI-open time. This is the explicit lesson from Phase 4 Batch 4b's live-verification findings
  (commit `9c7caa6`) and the design doc's "never trust an item displayed in an inventory as proof of
  authorization," generalized to GUI navigation state.
- The GUI must never assume the target player is still online by the time an action runs — a target can log
  off while the admin is still navigating the menu tree. Every action resolves the target fresh via
  `Bukkit.getPlayer(targetId)` and handles `null` by messaging the viewer and stopping, never by throwing.
- No new permission nodes: this batch reuses `aspects.admin` (open the GUI), `aspects.admin.set`,
  `aspects.admin.reroll`, `aspects.admin.give`, and `aspects.admin.cooldown`, all already registered in
  `plugin.yml` from Phase 4 Batch 4a. Do not add or rename any permission.
- No new unit tests: every class in this batch is pure Bukkit-API GUI code (`Inventory`, `ItemStack`,
  `SkullMeta`, `Player`) with no live Bukkit registry available in this project's plain-JUnit test
  environment — consistent with every other GUI class in this codebase (Phase 3 Batch 3b, Phase 4 Batch 4b).
  Each task's deliverable is verified by a clean `mvn package` (84 existing tests must keep passing) plus the
  batch-level manual Docker verification at the end, not new automated tests.
- Follow the established "duplicate the small helpers" convention: do not extract a shared base class or
  utility for `namedItem`/filler-building across these four new GUI classes or the pre-existing ones.

---

### Task 1: AdminAspectAssignGUI (assign a specific Aspect to a target player)

**Files:**
- Create: `src/main/java/com/aspects/smp/gui/AdminAspectAssignGUI.java`
- Modify: `src/main/resources/messages.yml` (append a new `gui.admin-assign` section)

**Interfaces:**
- Consumes: `AspectsGuiHolder` (existing interface, `getInventory()`/`onClick(InventoryClickEvent)`/
  `onClose(InventoryCloseEvent)`); `AspectsPlugin.getAspectRegistry().enabled() -> Collection<Aspect>`;
  `AspectsPlugin.getAspectManager().adminSet(Player, String) -> Optional<Aspect>`; `Aspect.id()`,
  `Aspect.definition().icon()/.displayName()/.description()/.rarity().color()/.rarity().name()`;
  `MessageUtil.parse(String)`, `MessageUtil.sendMessage(Player, String, Map<String,String>)`;
  `ConfigManager.message(String)`.
- Produces: `public AdminAspectAssignGUI(AspectsPlugin plugin, Player viewer, UUID targetId,
  String targetName, Runnable onBack)` and `public void open()` — the exact constructor and method later
  tasks (`AdminPlayerMenuGUI`) call to open this GUI. `targetName` is a snapshot of the target's name used
  for chat messages and does not need to be re-resolved (unlike the `Player` reference itself, which is
  re-resolved fresh via `targetId` at action time since the target could disconnect).

- [ ] **Step 1: Create the GUI class**

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
import java.util.Optional;
import java.util.UUID;

public final class AdminAspectAssignGUI implements AspectsGuiHolder {
    private static final int SIZE = 54;
    private static final int BACK_SLOT = 49;

    private final AspectsPlugin plugin;
    private final Player viewer;
    private final UUID targetId;
    private final String targetName;
    private final Runnable onBack;
    private final Inventory inventory;
    private final Map<Integer, Aspect> aspectBySlot = new HashMap<>();

    public AdminAspectAssignGUI(AspectsPlugin plugin, Player viewer, UUID targetId, String targetName, Runnable onBack) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.targetId = targetId;
        this.targetName = targetName;
        this.onBack = onBack;
        this.inventory = Bukkit.createInventory(this, SIZE,
                MessageUtil.parse(plugin.getConfigManager().message("gui.admin-assign.title")));
        populate();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        viewer.openInventory(inventory);
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
            inventory.setItem(slot, assignItem(aspect));
            aspectBySlot.put(slot, aspect);
            slot++;
        }

        inventory.setItem(BACK_SLOT, namedItem(Material.ARROW, plugin.getConfigManager().message("gui.admin-assign.back-name")));
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(name).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack assignItem(Aspect aspect) {
        ItemStack item = new ItemStack(aspect.definition().icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(aspect.definition().displayName()).decoration(TextDecoration.ITALIC, false));
        List<String> lore = List.of(
                "<" + aspect.definition().rarity().color() + ">" + aspect.definition().rarity().name() + "</" +
                        aspect.definition().rarity().color() + ">",
                aspect.definition().description(),
                " ",
                plugin.getConfigManager().message("gui.admin-assign.choose-lore"));
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
            viewer.closeInventory();
            onBack.run();
            return;
        }
        Aspect clicked = aspectBySlot.get(slot);
        if (clicked != null) {
            handleAssign(clicked);
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        // No item is ever consumed here; closing without choosing needs no cleanup.
    }

    private void handleAssign(Aspect chosen) {
        if (!viewer.hasPermission("aspects.admin.set")) {
            viewer.closeInventory();
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            viewer.sendMessage(MessageUtil.parse(plugin.getConfigManager().message("gui.admin-player.player-offline")));
            viewer.closeInventory();
            return;
        }
        Optional<Aspect> result = plugin.getAspectManager().adminSet(target, chosen.id());
        if (result.isEmpty()) {
            viewer.sendMessage(MessageUtil.parse("<red>Unknown or disabled Aspect id: " + chosen.id() + "</red>"));
            viewer.closeInventory();
            return;
        }
        Aspect aspect = result.get();
        viewer.sendMessage(MessageUtil.parse("<green>Set " + targetName + "'s Aspect to " + aspect.id() + ".</green>"));
        MessageUtil.sendMessage(target, plugin.getConfigManager().message("reroll.new"),
                Map.of("new", aspect.definition().displayName()));
        viewer.closeInventory();
        onBack.run();
    }
}
```

- [ ] **Step 2: Append the new message keys**

Open `src/main/resources/messages.yml` and append, immediately after the existing `main:` block (the file
currently ends at line 50 with `cooldown-remaining: "<red>{seconds}s remaining</red>"`):

```yaml
  admin-assign:
    title: "<dark_purple>Assign Aspect</dark_purple>"
    choose-lore: "<gray>Click to assign this Aspect.</gray>"
    back-name: "<yellow>« Back</yellow>"
  admin-player:
    player-offline: "<red>That player is no longer online.</red>"
```

(The `admin-player.player-offline` key is introduced here because this task's `handleAssign` already needs
it; Task 3 adds the rest of the `admin-player` section below this key — YAML keys can be appended to the
same top-level section across tasks without conflict as long as no key is duplicated.)

- [ ] **Step 3: Build and verify**

```bash
export JAVA_HOME="/home/erikkalstrom/.local/share/flatpak/runtime/org.freedesktop.Sdk.Extension.openjdk21/x86_64/26.08/d4d21c831d4b2abcc5c2c1f32d7ac4a5cac886db0328a94a259021e26e93689a/files/jvm/openjdk-21"
export PATH="$JAVA_HOME/bin:/tmp/apache-maven-3.9.9/bin:$PATH"
mvn -q package
```
Expected: `BUILD SUCCESS`, all 84 existing tests still pass (no new tests added — see Global Constraints).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/aspects/smp/gui/AdminAspectAssignGUI.java src/main/resources/messages.yml
git commit -m "$(cat <<'EOF'
Add AdminAspectAssignGUI for assigning a specific Aspect from the admin GUI

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: AdminRerollConfirmGUI (force-reroll with confirmation)

**Files:**
- Create: `src/main/java/com/aspects/smp/gui/AdminRerollConfirmGUI.java`
- Modify: `src/main/resources/messages.yml` (append a new `gui.admin-reroll-confirm` section)

**Interfaces:**
- Consumes: same `AspectsGuiHolder` interface and `AspectsPlugin` accessors as Task 1, plus
  `AspectsPlugin.getAspectManager().adminReroll(Player) -> Optional<Aspect>` and
  `AspectsPlugin.getAspectManager().getAspect(UUID) -> Optional<Aspect>`. Uses the existing
  `gui.admin-player.player-offline` message key added in Task 1.
- Produces: `public AdminRerollConfirmGUI(AspectsPlugin plugin, Player viewer, UUID targetId,
  String targetName, Runnable onBack)` and `public void open()` — the exact constructor Task 3
  (`AdminPlayerMenuGUI`) calls to open this GUI.

- [ ] **Step 1: Create the GUI class**

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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class AdminRerollConfirmGUI implements AspectsGuiHolder {
    private static final int SIZE = 9;
    private static final int CURRENT_ASPECT_SLOT = 4;
    private static final int CONFIRM_SLOT = 2;
    private static final int CANCEL_SLOT = 6;

    private final AspectsPlugin plugin;
    private final Player viewer;
    private final UUID targetId;
    private final String targetName;
    private final Runnable onBack;
    private final Inventory inventory;

    public AdminRerollConfirmGUI(AspectsPlugin plugin, Player viewer, UUID targetId, String targetName, Runnable onBack) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.targetId = targetId;
        this.targetName = targetName;
        this.onBack = onBack;
        this.inventory = Bukkit.createInventory(this, SIZE,
                MessageUtil.parse(plugin.getConfigManager().message("gui.admin-reroll-confirm.title")));
        populate();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    private void populate() {
        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }
        inventory.setItem(CURRENT_ASPECT_SLOT, currentAspectItem());
        inventory.setItem(CONFIRM_SLOT,
                namedItem(Material.LIME_WOOL, plugin.getConfigManager().message("gui.admin-reroll-confirm.confirm-name")));
        inventory.setItem(CANCEL_SLOT,
                namedItem(Material.RED_WOOL, plugin.getConfigManager().message("gui.admin-reroll-confirm.cancel-name")));
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(name).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack currentAspectItem() {
        Aspect aspect = plugin.getAspectManager().getAspect(targetId).orElse(null);
        String name = aspect != null ? aspect.definition().displayName() : "<red>No Aspect</red>";
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MessageUtil.parse(plugin.getConfigManager().message("gui.admin-reroll-confirm.current-aspect-lore"))
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
            viewer.closeInventory();
            onBack.run();
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        // Closing without confirming is cancellation; nothing to undo.
    }

    private void handleConfirm() {
        if (!viewer.hasPermission("aspects.admin.reroll")) {
            viewer.closeInventory();
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            viewer.sendMessage(MessageUtil.parse(plugin.getConfigManager().message("gui.admin-player.player-offline")));
            viewer.closeInventory();
            return;
        }
        Aspect previous = plugin.getAspectManager().getAspect(targetId).orElse(null);
        Optional<Aspect> result = plugin.getAspectManager().adminReroll(target);
        if (result.isEmpty()) {
            viewer.sendMessage(MessageUtil.parse("<red>No eligible Aspect was available to reroll into.</red>"));
            viewer.closeInventory();
            onBack.run();
            return;
        }
        Aspect newAspect = result.get();
        viewer.sendMessage(MessageUtil.parse("<green>Rerolled " + targetName + "'s Aspect to " + newAspect.id() + ".</green>"));
        MessageUtil.sendMessage(target, plugin.getConfigManager().message("reroll.changed"));
        if (previous != null) {
            MessageUtil.sendMessage(target, plugin.getConfigManager().message("reroll.previous"),
                    Map.of("old", previous.definition().displayName()));
        }
        MessageUtil.sendMessage(target, plugin.getConfigManager().message("reroll.new"),
                Map.of("new", newAspect.definition().displayName()));
        viewer.closeInventory();
        onBack.run();
    }
}
```

- [ ] **Step 2: Append the new message keys**

Append to `src/main/resources/messages.yml`, after the `admin-player:` block added in Task 1:

```yaml
  admin-reroll-confirm:
    title: "<dark_purple>Force Reroll?</dark_purple>"
    current-aspect-lore: "<gray>This Aspect will be replaced.</gray>"
    confirm-name: "<green>Confirm Reroll</green>"
    cancel-name: "<red>Cancel</red>"
```

- [ ] **Step 3: Build and verify**

```bash
export JAVA_HOME="/home/erikkalstrom/.local/share/flatpak/runtime/org.freedesktop.Sdk.Extension.openjdk21/x86_64/26.08/d4d21c831d4b2abcc5c2c1f32d7ac4a5cac886db0328a94a259021e26e93689a/files/jvm/openjdk-21"
export PATH="$JAVA_HOME/bin:/tmp/apache-maven-3.9.9/bin:$PATH"
mvn -q package
```
Expected: `BUILD SUCCESS`, all 84 tests still pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/aspects/smp/gui/AdminRerollConfirmGUI.java src/main/resources/messages.yml
git commit -m "$(cat <<'EOF'
Add AdminRerollConfirmGUI for force-rerolling a player with confirmation

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: AdminPlayerMenuGUI (per-player sub-menu)

**Files:**
- Create: `src/main/java/com/aspects/smp/gui/AdminPlayerMenuGUI.java`
- Modify: `src/main/resources/messages.yml` (append the rest of the `gui.admin-player` section)

**Interfaces:**
- Consumes: `AdminAspectAssignGUI(AspectsPlugin, Player, UUID, String, Runnable)` and
  `AdminRerollConfirmGUI(AspectsPlugin, Player, UUID, String, Runnable)` from Tasks 1-2;
  `AspectsPlugin.getCustomItemManager().createReroller() -> ItemStack`;
  `AspectsPlugin.getCooldownManager().clearCache(UUID)`; `Player.getInventory().addItem(ItemStack) ->
  Map<Integer, ItemStack>` (leftover); `World.dropItemNaturally(Location, ItemStack)`.
- Produces: `public AdminPlayerMenuGUI(AspectsPlugin plugin, Player viewer, UUID targetId,
  String targetName, Runnable onBack)` and `public void open()` — the exact constructor Task 4
  (`AdminGUI`) calls when an admin clicks a player's head, and that Tasks 1-2's `onBack` callbacks call to
  return here after a leaf action completes.

- [ ] **Step 1: Create the GUI class**

```java
package com.aspects.smp.gui;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.Aspect;
import com.aspects.smp.item.CustomItemManager;
import com.aspects.smp.util.MessageUtil;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdminPlayerMenuGUI implements AspectsGuiHolder {
    private static final int SIZE = 27;
    private static final int CURRENT_ASPECT_SLOT = 13;
    private static final int ASSIGN_SLOT = 10;
    private static final int REROLL_SLOT = 12;
    private static final int GIVE_REROLLER_SLOT = 14;
    private static final int CLEAR_COOLDOWN_SLOT = 16;
    private static final int BACK_SLOT = 22;

    private final AspectsPlugin plugin;
    private final Player viewer;
    private final UUID targetId;
    private final String targetName;
    private final Runnable onBack;
    private final Inventory inventory;

    public AdminPlayerMenuGUI(AspectsPlugin plugin, Player viewer, UUID targetId, String targetName, Runnable onBack) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.targetId = targetId;
        this.targetName = targetName;
        this.onBack = onBack;
        this.inventory = Bukkit.createInventory(this, SIZE,
                MessageUtil.parse(plugin.getConfigManager().message("gui.admin-player.title-prefix") + targetName));
        populate();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    private void populate() {
        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }
        inventory.setItem(CURRENT_ASPECT_SLOT, currentAspectItem());
        inventory.setItem(ASSIGN_SLOT, namedItem(Material.NAME_TAG, plugin.getConfigManager().message("gui.admin-player.assign-name")));
        inventory.setItem(REROLL_SLOT, namedItem(Material.NETHER_STAR, plugin.getConfigManager().message("gui.admin-player.reroll-name")));
        inventory.setItem(GIVE_REROLLER_SLOT, namedItem(Material.AMETHYST_SHARD, plugin.getConfigManager().message("gui.admin-player.give-reroller-name")));
        inventory.setItem(CLEAR_COOLDOWN_SLOT, namedItem(Material.CLOCK, plugin.getConfigManager().message("gui.admin-player.clear-cooldown-name")));
        inventory.setItem(BACK_SLOT, namedItem(Material.ARROW, plugin.getConfigManager().message("gui.admin-player.back-name")));
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(name).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack currentAspectItem() {
        Aspect aspect = plugin.getAspectManager().getAspect(targetId).orElse(null);
        String name = aspect != null ? aspect.definition().displayName()
                : plugin.getConfigManager().message("gui.admin-player.no-aspect");
        Material icon = aspect != null ? aspect.definition().icon() : Material.BARRIER;
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MessageUtil.parse(plugin.getConfigManager().message("gui.admin-player.current-aspect-lore"))
                        .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!inventory.equals(event.getClickedInventory())) {
            return;
        }
        switch (event.getSlot()) {
            case ASSIGN_SLOT -> {
                viewer.closeInventory();
                new AdminAspectAssignGUI(plugin, viewer, targetId, targetName,
                        () -> new AdminPlayerMenuGUI(plugin, viewer, targetId, targetName, onBack).open()).open();
            }
            case REROLL_SLOT -> {
                viewer.closeInventory();
                new AdminRerollConfirmGUI(plugin, viewer, targetId, targetName,
                        () -> new AdminPlayerMenuGUI(plugin, viewer, targetId, targetName, onBack).open()).open();
            }
            case GIVE_REROLLER_SLOT -> handleGiveReroller();
            case CLEAR_COOLDOWN_SLOT -> handleClearCooldown();
            case BACK_SLOT -> {
                viewer.closeInventory();
                onBack.run();
            }
            default -> {
            }
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        // No state to clean up — every action here applies immediately, nothing is held pending.
    }

    private void handleGiveReroller() {
        if (!viewer.hasPermission("aspects.admin.give")) {
            viewer.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            viewer.sendMessage(MessageUtil.parse(plugin.getConfigManager().message("gui.admin-player.player-offline")));
            return;
        }
        CustomItemManager itemManager = plugin.getCustomItemManager();
        ItemStack stack = itemManager.createReroller();
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            Location dropLocation = target.getLocation();
            World world = target.getWorld();
            for (ItemStack remaining : leftover.values()) {
                world.dropItemNaturally(dropLocation, remaining);
            }
            viewer.sendMessage(MessageUtil.parse("<yellow>" + targetName +
                    "'s inventory was full — the remainder was dropped at their feet.</yellow>"));
        }
        plugin.getLogger().info(viewer.getName() + " gave 1 Aspect Reroller to " + targetName + " via the admin GUI");
        viewer.sendMessage(MessageUtil.parse("<green>Gave 1 Aspect Reroller to " + targetName + ".</green>"));
    }

    private void handleClearCooldown() {
        if (!viewer.hasPermission("aspects.admin.cooldown")) {
            viewer.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            viewer.sendMessage(MessageUtil.parse(plugin.getConfigManager().message("gui.admin-player.player-offline")));
            return;
        }
        plugin.getCooldownManager().clearCache(targetId);
        viewer.sendMessage(MessageUtil.parse("<green>Cleared all ability cooldowns for " + targetName + ".</green>"));
    }
}
```

- [ ] **Step 2: Append the remaining message keys**

Append to `src/main/resources/messages.yml`, filling out the `admin-player:` block started in Task 1 (add
these keys under the existing `admin-player:` key — do not create a second `admin-player:` section):

```yaml
    title-prefix: "<dark_purple>Manage: </dark_purple>"
    current-aspect-lore: "<gray>Current Aspect</gray>"
    no-aspect: "<red>No Aspect assigned</red>"
    assign-name: "<yellow>Assign Aspect</yellow>"
    reroll-name: "<gold>Force Reroll</gold>"
    give-reroller-name: "<green>Give Reroller</green>"
    clear-cooldown-name: "<blue>Clear Cooldowns</blue>"
    back-name: "<yellow>« Back</yellow>"
```

After this step, the full `admin-player:` block (combining Task 1's `player-offline` key with these) must
read:

```yaml
  admin-player:
    player-offline: "<red>That player is no longer online.</red>"
    title-prefix: "<dark_purple>Manage: </dark_purple>"
    current-aspect-lore: "<gray>Current Aspect</gray>"
    no-aspect: "<red>No Aspect assigned</red>"
    assign-name: "<yellow>Assign Aspect</yellow>"
    reroll-name: "<gold>Force Reroll</gold>"
    give-reroller-name: "<green>Give Reroller</green>"
    clear-cooldown-name: "<blue>Clear Cooldowns</blue>"
    back-name: "<yellow>« Back</yellow>"
```

- [ ] **Step 3: Build and verify**

```bash
export JAVA_HOME="/home/erikkalstrom/.local/share/flatpak/runtime/org.freedesktop.Sdk.Extension.openjdk21/x86_64/26.08/d4d21c831d4b2abcc5c2c1f32d7ac4a5cac886db0328a94a259021e26e93689a/files/jvm/openjdk-21"
export PATH="$JAVA_HOME/bin:/tmp/apache-maven-3.9.9/bin:$PATH"
mvn -q package
```
Expected: `BUILD SUCCESS`, all 84 tests still pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/aspects/smp/gui/AdminPlayerMenuGUI.java src/main/resources/messages.yml
git commit -m "$(cat <<'EOF'
Add AdminPlayerMenuGUI with assign/reroll/give/clear-cooldown actions

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: AdminGUI (top-level player list) + wire `/aspects admin` to open it

**Files:**
- Create: `src/main/java/com/aspects/smp/gui/AdminGUI.java`
- Modify: `src/main/java/com/aspects/smp/command/AdminCommand.java:25-30` (the `args.length == 0` branch)
- Modify: `src/main/resources/messages.yml` (append a new `gui.admin` section)

**Interfaces:**
- Consumes: `AdminPlayerMenuGUI(AspectsPlugin, Player, UUID, String, Runnable)` from Task 3;
  `AspectBrowserGUI(AspectsPlugin, Player, Runnable)` (already exists, Phase 3 Batch 3b — reused directly,
  unmodified, for the "View Aspect List" button); `Bukkit.getOnlinePlayers() -> Collection<? extends
  Player>`; `SkullMeta.setOwningPlayer(OfflinePlayer)`.
- Produces: `public AdminGUI(AspectsPlugin plugin, Player viewer)` and `public void open()` — called from
  `AdminCommand.handle` when `args.length == 0` and the sender is a permitted in-game player.

- [ ] **Step 1: Create the GUI class**

```java
package com.aspects.smp.gui;

import com.aspects.smp.AspectsPlugin;
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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AdminGUI implements AspectsGuiHolder {
    private static final int SIZE = 54;
    private static final int MAX_PLAYER_SLOTS = 45;
    private static final int VIEW_ASPECTS_SLOT = 49;
    private static final int NO_PLAYERS_SLOT = 22;

    private final AspectsPlugin plugin;
    private final Player viewer;
    private final Inventory inventory;
    private final Map<Integer, Player> playerBySlot = new HashMap<>();

    public AdminGUI(AspectsPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, SIZE,
                MessageUtil.parse(plugin.getConfigManager().message("gui.admin.title")));
        populate();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    private void populate() {
        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= MAX_PLAYER_SLOTS) {
                break;
            }
            inventory.setItem(slot, playerHeadItem(online));
            playerBySlot.put(slot, online);
            slot++;
        }
        if (slot == 0) {
            inventory.setItem(NO_PLAYERS_SLOT, namedItem(Material.BARRIER, plugin.getConfigManager().message("gui.admin.no-players-name")));
        }

        inventory.setItem(VIEW_ASPECTS_SLOT, namedItem(Material.BOOK, plugin.getConfigManager().message("gui.admin.view-aspects-name")));
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(name).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack playerHeadItem(Player online) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(online);
        meta.displayName(MessageUtil.parse("<yellow>" + online.getName() + "</yellow>").decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!inventory.equals(event.getClickedInventory())) {
            return;
        }
        int slot = event.getSlot();
        if (slot == VIEW_ASPECTS_SLOT) {
            viewer.closeInventory();
            new AspectBrowserGUI(plugin, viewer, () -> new AdminGUI(plugin, viewer).open()).open();
            return;
        }
        Player clicked = playerBySlot.get(slot);
        if (clicked != null) {
            viewer.closeInventory();
            UUID targetId = clicked.getUniqueId();
            String targetName = clicked.getName();
            new AdminPlayerMenuGUI(plugin, viewer, targetId, targetName, () -> new AdminGUI(plugin, viewer).open()).open();
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        // No state to clean up — this GUI never mutates anything.
    }
}
```

- [ ] **Step 2: Wire `/aspects admin` (no args) to open the GUI**

In `src/main/java/com/aspects/smp/command/AdminCommand.java`, add the import:

```java
import com.aspects.smp.gui.AdminGUI;
```

Replace the current `args.length == 0` branch (lines 26-29):

```java
        if (args.length == 0) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>Usage: /aspects admin <set|reroll|reset|give-reroller|give|give-selection|clear-cooldown|reload|list|debug> ...</red>"));
            return true;
        }
```

with:

```java
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.parse(
                        "<red>Usage: /aspects admin <set|reroll|reset|give-reroller|give|give-selection|clear-cooldown|reload|list|debug> ...</red>"));
                return true;
            }
            if (!player.hasPermission("aspects.admin")) {
                player.sendMessage(MessageUtil.parse("<red>You do not have permission to do that.</red>"));
                return true;
            }
            new AdminGUI(plugin, player).open();
            return true;
        }
```

`Player` is already imported in this file (used throughout the existing handlers), so no additional import
is needed for that type.

- [ ] **Step 3: Append the new message keys**

Append to `src/main/resources/messages.yml`, after the `admin-reroll-confirm:` block added in Task 2:

```yaml
  admin:
    title: "<dark_purple>Aspect Admin</dark_purple>"
    view-aspects-name: "<aqua>View Aspect List</aqua>"
    no-players-name: "<red>No players online</red>"
```

- [ ] **Step 4: Build and verify**

```bash
export JAVA_HOME="/home/erikkalstrom/.local/share/flatpak/runtime/org.freedesktop.Sdk.Extension.openjdk21/x86_64/26.08/d4d21c831d4b2abcc5c2c1f32d7ac4a5cac886db0328a94a259021e26e93689a/files/jvm/openjdk-21"
export PATH="$JAVA_HOME/bin:/tmp/apache-maven-3.9.9/bin:$PATH"
mvn -q package
```
Expected: `BUILD SUCCESS`, all 84 tests still pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/aspects/smp/gui/AdminGUI.java src/main/java/com/aspects/smp/command/AdminCommand.java src/main/resources/messages.yml
git commit -m "$(cat <<'EOF'
Add AdminGUI player-list menu and wire /aspects admin (no args) to open it

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

## Batch-level manual verification (after all 4 tasks)

Following this project's established pattern (every prior batch ends with live Docker verification, not
per-task), after all four tasks are committed:

1. Deploy the built jar to the `aspects-test` Docker container and confirm it starts healthy.
2. As an operator, run `/aspects admin` with no arguments — confirm `AdminGUI` opens showing player heads
   for all online test bots plus a "View Aspect List" book item.
3. Click a player's head — confirm `AdminPlayerMenuGUI` opens showing that player's current Aspect and the
   four action buttons plus Back.
4. Click "Assign Aspect" — confirm `AdminAspectAssignGUI` opens with all enabled Aspects; click one and
   confirm the target's Aspect actually changes (verify via `/aspects admin debug <player>`) and the viewer
   lands back on `AdminPlayerMenuGUI` showing the updated Aspect.
5. Click "Force Reroll" — confirm `AdminRerollConfirmGUI` opens; click Confirm and verify the Aspect changes
   again (verify via debug); click Cancel on a separate attempt and verify the Aspect is unchanged.
6. Click "Give Reroller" — confirm the target receives exactly one Reroller (check inventory) and the admin
   sees a success message, without the GUI closing.
7. Click "Clear Cooldowns" — put the target's ability on cooldown first (e.g. via its active ability), then
   clear it from the GUI and confirm `/aspects admin debug <player>` reports "ready."
8. Revoke `aspects.admin.reroll` from the viewing admin (e.g. via a LuckPerms-equivalent test op/deop or a
   permissions plugin if available; otherwise simulate by removing operator status, which strips every
   `default: op` node at once) while `AdminRerollConfirmGUI` is still open from an earlier, privileged state,
   then click Confirm — verify the reroll is refused (no Aspect change) rather than silently succeeding,
   confirming the click-time re-check actually works.
9. Click "View Aspect List" from the main `AdminGUI` — confirm the existing, unmodified `AspectBrowserGUI`
   opens read-only, and its Back button returns to `AdminGUI`.
10. Log the full verdict (pass/fail per step, and any findings) to `.superpowers/sdd/progress.md`, matching
    the detail level of every prior batch's live-verification ledger entry.

## What's deliberately out of scope here

Everything listed as out of scope in the Phase 4 design doc remains out of scope: WorldGuard integration
(Phase 5), and no new config files or permission nodes. Pagination for the online-player list (if a server
ever exceeds 45 concurrent players) is not implemented — `MAX_PLAYER_SLOTS = 45` silently stops listing
further players, matching this codebase's existing precedent of capping list-style GUIs at their available
slot count (`AspectBrowserGUI`'s `BACK_SLOT`-based cap, `AspectSelectorGUI`'s `CANCEL_SLOT`-based cap) rather
than adding pagination nobody has asked for.
