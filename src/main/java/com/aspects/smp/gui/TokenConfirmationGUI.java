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
