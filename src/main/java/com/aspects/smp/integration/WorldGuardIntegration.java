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
        if (hasRegionBypass(player, location)) {
            return true;
        }
        return hook.isAbilityAllowed(location, player);
    }

    public boolean isPvpAllowed(Location casterLocation, Location targetLocation, Player caster) {
        if (!present || hook == null) {
            return true;
        }
        if (hasRegionBypass(caster, casterLocation)) {
            return true;
        }
        return hook.isPvpAllowed(casterLocation, targetLocation, caster);
    }

    /**
     * WorldGuard's own region-bypass permission ({@code worldguard.region.bypass.<world>}, typically
     * granted to admins/moderators) exempts a player from every region restriction WorldGuard itself
     * enforces — but {@link com.sk89q.worldguard.protection.regions.RegionQuery} does not apply it
     * automatically; that only happens inside WorldGuard's own listeners. Checking the documented
     * permission node directly here keeps bypass-holders consistent between WorldGuard's own
     * enforcement and this plugin's.
     */
    private boolean hasRegionBypass(Player player, Location location) {
        return player.hasPermission("worldguard.region.bypass." + location.getWorld().getName());
    }
}
