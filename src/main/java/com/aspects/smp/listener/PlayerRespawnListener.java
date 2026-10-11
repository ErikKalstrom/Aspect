package com.aspects.smp.listener;

import com.aspects.smp.AspectsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class PlayerRespawnListener implements Listener {
    private final AspectsPlugin plugin;

    public PlayerRespawnListener(AspectsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTask(plugin,
                () -> plugin.getAspectManager().reapplyPassive(event.getPlayer()));
    }
}
