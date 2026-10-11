package com.aspects.smp.ability;

import com.aspects.smp.integration.WorldGuardIntegration;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class ProtectionGate {
    private final Set<String> blockedWorlds;
    private final WorldGuardIntegration worldGuardIntegration;
    private final boolean respectWorldGuard;

    public ProtectionGate(Collection<String> blockedWorlds, WorldGuardIntegration worldGuardIntegration,
                           boolean respectWorldGuard) {
        this.blockedWorlds = new HashSet<>(blockedWorlds);
        this.worldGuardIntegration = worldGuardIntegration;
        this.respectWorldGuard = respectWorldGuard;
    }

    public boolean isAllowed(Player player) {
        if (blockedWorlds.contains(player.getWorld().getName())) {
            return false;
        }
        return isLocationAllowed(player.getLocation(), player);
    }

    /**
     * Checks only the WorldGuard restriction (not blocked-worlds) for an arbitrary location, honoring the
     * {@code respect-worldguard} config toggle. Used by abilities that need to check a candidate location
     * other than the player's current one, such as a teleport destination.
     */
    public boolean isLocationAllowed(Location location, Player player) {
        return !respectWorldGuard || worldGuardIntegration.isAbilityAllowed(location, player);
    }
}
