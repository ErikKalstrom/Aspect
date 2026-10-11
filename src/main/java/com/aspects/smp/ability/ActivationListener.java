package com.aspects.smp.ability;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ActivationListener implements Listener {
    private final AbilityManager abilityManager;
    private final JavaPlugin plugin;
    private final Set<UUID> debounce = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public ActivationListener(AbilityManager abilityManager, JavaPlugin plugin) {
        this.abilityManager = abilityManager;
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

        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        if (player.getInventory().getItemInMainHand().getType() != Material.AIR) {
            return;
        }
        if (!debounce.add(player.getUniqueId())) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> debounce.remove(player.getUniqueId()));

        AbilityManager.ActivationResult result = abilityManager.tryActivate(player);
        if (!result.success()) {
            player.sendActionBar(Component.text(result.failureMessage()));
        }
    }
}
