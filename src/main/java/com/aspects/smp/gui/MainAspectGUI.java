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
        lore.add(plugin.getConfigManager().message("gui.details.passive-label") + " " + aspect.definition().passiveName());
        lore.add("<gray>" + aspect.definition().passiveDescription() + "</gray>");
        lore.add(plugin.getConfigManager().message("gui.details.active-label") + " " + aspect.definition().activeName());
        lore.add("<gray>" + aspect.definition().activeDescription() + "</gray>");
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
        if (!recipes.rerollerEnabled()) {
            lore.add(plugin.getConfigManager().message("gui.main.craft-info-disabled"));
        } else {
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
