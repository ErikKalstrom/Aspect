package com.aspects.smp.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class WorldGuardIntegration {
    private static final String FLAG_NAME = "aspects-abilities";

    private final boolean present;
    private StateFlag abilitiesFlag;

    public WorldGuardIntegration(Plugin plugin) {
        Plugin worldGuard = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        this.present = worldGuard != null && worldGuard.isEnabled();
        if (present) {
            registerFlag(plugin);
        }
    }

    public boolean isPresent() {
        return present;
    }

    public boolean isAbilityAllowed(Location location, Player player) {
        if (!present || abilitiesFlag == null) {
            return true;
        }
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        return query.testState(BukkitAdapter.adapt(location), localPlayer, abilitiesFlag);
    }

    private void registerFlag(Plugin plugin) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StateFlag flag = new StateFlag(FLAG_NAME, true);
            registry.register(flag);
            this.abilitiesFlag = flag;
        } catch (FlagConflictException e) {
            Flag<?> existing = registry.get(FLAG_NAME);
            if (existing instanceof StateFlag stateFlag) {
                this.abilitiesFlag = stateFlag;
            } else {
                plugin.getLogger().warning("Could not register the '" + FLAG_NAME +
                        "' WorldGuard flag: " + e.getMessage());
            }
        }
    }
}
