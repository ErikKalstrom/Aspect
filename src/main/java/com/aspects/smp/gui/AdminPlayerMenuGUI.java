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
