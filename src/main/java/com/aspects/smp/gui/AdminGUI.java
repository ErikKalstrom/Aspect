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
