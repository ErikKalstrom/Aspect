package com.aspects.smp.ability;

import com.aspects.smp.integration.WorldGuardIntegration;
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
        if (respectWorldGuard && !worldGuardIntegration.isAbilityAllowed(player.getLocation(), player)) {
            return false;
        }
        return true;
    }
}
