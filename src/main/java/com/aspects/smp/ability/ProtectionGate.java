package com.aspects.smp.ability;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class ProtectionGate {
    private final Set<String> blockedWorlds;

    public ProtectionGate(Collection<String> blockedWorlds) {
        this.blockedWorlds = new HashSet<>(blockedWorlds);
    }

    public boolean isAllowed(Player player) {
        return !blockedWorlds.contains(player.getWorld().getName());
    }
}
