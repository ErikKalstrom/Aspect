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

    /**
     * Checks whether {@code caster} may deal offensive-ability damage or debuffs to {@code target}:
     * the target's world must have PvP enabled, and (if WorldGuard is respected) its own built-in
     * PVP flag must allow it at the target's location. Independent of {@link #isLocationAllowed},
     * which only gates whether the custom aspects-abilities flag permits activating the ability at
     * all — an ability can be allowed to activate but still not be allowed to hit a given target.
     */
    @SuppressWarnings("deprecation") // World#getPVP() has no non-deprecated replacement in the Paper API
    public boolean canTargetPlayer(Player caster, Player target) {
        if (!target.getWorld().getPVP()) {
            return false;
        }
        return !respectWorldGuard || worldGuardIntegration.isPvpAllowed(target.getLocation(), caster);
    }
}
