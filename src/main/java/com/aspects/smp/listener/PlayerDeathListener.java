package com.aspects.smp.listener;

import com.aspects.smp.AspectsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class PlayerDeathListener implements Listener {
    private final AspectsPlugin plugin;

    public PlayerDeathListener(AspectsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.getTemporaryEffectManager().clear(event.getEntity().getUniqueId());
    }
}
