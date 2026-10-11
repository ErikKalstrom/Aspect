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
