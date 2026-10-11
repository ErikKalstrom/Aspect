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
        String capitalizedId = aspect.definition().id().substring(0, 1).toUpperCase(java.util.Locale.ROOT) +
                aspect.definition().id().substring(1);
        List<String> lore = List.of(
                "<" + aspect.definition().rarity().color() + ">" + aspect.definition().rarity().name() + "</" +
                        aspect.definition().rarity().color() + ">",
                aspect.definition().description(),
                " ",
                plugin.getConfigManager().message("gui.details.passive-label") + " " + capitalizedId + " passive",
                plugin.getConfigManager().message("gui.details.active-label") + " " + capitalizedId + " active ability",
                plugin.getConfigManager().message("gui.details.cooldown-label") + " " +
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
