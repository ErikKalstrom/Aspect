package com.aspects.smp.ability;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ActivationListener implements Listener {
    private final AbilityManager abilityManager;
    private final AspectsPlugin plugin;
    private final Set<UUID> debounce = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public ActivationListener(AbilityManager abilityManager, AspectsPlugin plugin) {
        this.abilityManager = abilityManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        if (player.getInventory().getItemInMainHand().getType() != Material.AIR) {
            return;
        }
        event.setCancelled(true);

        if (!debounce.add(player.getUniqueId())) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> debounce.remove(player.getUniqueId()));

        AbilityManager.ActivationResult result = abilityManager.tryActivate(player);
        if (!result.success()) {
            String template = plugin.getConfigManager().message(result.messageKey());
            player.sendActionBar(MessageUtil.parse(template, result.placeholders()));
        }
    }
}
