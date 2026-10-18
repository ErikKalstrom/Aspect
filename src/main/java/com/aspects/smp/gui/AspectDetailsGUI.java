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
        lore.add(plugin.getConfigManager().message("gui.details.passive-label") + " " + aspect.definition().passiveName());
        if (!aspect.definition().passiveDescription().isBlank()) {
            lore.add("<gray>" + aspect.definition().passiveDescription() + "</gray>");
        }
        lore.add(plugin.getConfigManager().message("gui.details.active-label") + " " + aspect.definition().activeName());
        if (!aspect.definition().activeDescription().isBlank()) {
            lore.add("<gray>" + aspect.definition().activeDescription() + "</gray>");
        }
        lore.add(plugin.getConfigManager().message("gui.details.cooldown-label") + " " +
                (long) aspect.definition().getDouble("active.cooldown-seconds", 20.0) + "s");

        meta.lore(lore.stream().map(line -> MessageUtil.parse(line).decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
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
