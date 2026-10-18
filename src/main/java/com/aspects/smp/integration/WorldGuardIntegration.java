package com.aspects.smp.integration;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class WorldGuardIntegration {
    private final boolean present;
    private final WorldGuardHook hook;

    public WorldGuardIntegration(Plugin plugin) {
        Plugin worldGuard = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        this.present = worldGuard != null;
        this.hook = present ? new WorldGuardHook(plugin) : null;
    }

    public boolean isPresent() {
        return present;
    }

    public boolean isAbilityAllowed(Location location, Player player) {
        if (!present || hook == null) {
            return true;
        }
        return hook.isAbilityAllowed(location, player);
    }

    public boolean isPvpAllowed(Location location, Player actor) {
        if (!present || hook == null) {
            return true;
        }
        return hook.isPvpAllowed(location, actor);
    }
}
