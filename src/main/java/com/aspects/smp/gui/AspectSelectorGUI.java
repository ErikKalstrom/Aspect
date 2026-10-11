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
            if (!player.hasPermission("aspects.admin.selector")) {
                player.closeInventory();
                return;
            }
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
