package com.aspects.smp.item;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.gui.RerollConfirmationGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class RerollerInteractListener implements Listener {
    private final AspectsPlugin plugin;

    public RerollerInteractListener(AspectsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!plugin.getCustomItemManager().isReroller(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        int handSlot = player.getInventory().getHeldItemSlot();
        new RerollConfirmationGUI(plugin, player, handSlot).open();
    }
}
