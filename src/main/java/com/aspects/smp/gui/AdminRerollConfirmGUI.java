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
