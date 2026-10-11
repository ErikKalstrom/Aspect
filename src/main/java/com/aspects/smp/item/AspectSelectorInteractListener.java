package com.aspects.smp.item;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.gui.AspectSelectorGUI;
import com.aspects.smp.gui.TokenConfirmationGUI;
import com.aspects.smp.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public final class AspectSelectorInteractListener implements Listener {
    private final AspectsPlugin plugin;

    public AspectSelectorInteractListener(AspectsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();

        // Check both hands directly rather than trusting event.getHand()/event.getItem(): Bukkit can
        // misreport the hand as MAIN_HAND when the main hand is empty and only the off-hand holds the
        // item actually used, which would otherwise let a tagged item in the off-hand slot slip through
        // unprotected (e.g. a specific-Aspect token triggering its underlying Ender Eye's vanilla throw).
        ItemStack offHandItem = player.getInventory().getItemInOffHand();
        boolean offHandIsSelectorItem = plugin.getCustomItemManager().getSelectorTokenAspectId(offHandItem).isPresent()
                || plugin.getCustomItemManager().isGenericSelector(offHandItem);
        if (offHandIsSelectorItem) {
            event.setCancelled(true);
            event.setUseItemInHand(Event.Result.DENY);
        }

        ItemStack mainHandItem = player.getInventory().getItemInMainHand();

        Optional<String> tokenAspectId = plugin.getCustomItemManager().getSelectorTokenAspectId(mainHandItem);
        if (tokenAspectId.isPresent()) {
            event.setCancelled(true);
            event.setUseItemInHand(Event.Result.DENY);
            if (!player.hasPermission("aspects.admin.give")) {
                player.sendMessage(MessageUtil.parse("<red>You do not have permission to use this item.</red>"));
                return;
            }
            int handSlot = player.getInventory().getHeldItemSlot();
            new TokenConfirmationGUI(plugin, player, handSlot, tokenAspectId.get()).open();
            return;
        }

        if (plugin.getCustomItemManager().isGenericSelector(mainHandItem)) {
            event.setCancelled(true);
            event.setUseItemInHand(Event.Result.DENY);
            if (!player.hasPermission("aspects.admin.selector")) {
                player.sendMessage(MessageUtil.parse("<red>You do not have permission to use this item.</red>"));
                return;
            }
            int handSlot = player.getInventory().getHeldItemSlot();
            new AspectSelectorGUI(plugin, player, handSlot).open();
        }
    }
}
