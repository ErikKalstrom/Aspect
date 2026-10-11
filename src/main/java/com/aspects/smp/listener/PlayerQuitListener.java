package com.aspects.smp.listener;

import com.aspects.smp.AspectsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerQuitListener implements Listener {
    private final AspectsPlugin plugin;

    public PlayerQuitListener(AspectsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getAspectManager().unload(event.getPlayer());
    }
}
