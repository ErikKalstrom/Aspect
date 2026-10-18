package com.aspects.smp.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Holds every WorldGuard/WorldEdit API reference. Kept out of {@link WorldGuardIntegration} itself so that
 * class never needs to resolve a WorldGuard class during JVM verification — the verifier resolves exception
 * handler types and checkcast/instanceof targets for a class as soon as it is linked, regardless of which
 * branches actually run, so a presence-check guard inside the same class is not enough to keep it loadable
 * when WorldGuard is absent. This class is only ever constructed after presence is confirmed.
 */
final class WorldGuardHook {
    private static final String FLAG_NAME = "aspects-abilities";

    private StateFlag abilitiesFlag;

    WorldGuardHook(Plugin plugin) {
        registerFlag(plugin);
    }

    boolean isAbilityAllowed(Location location, Player player) {
        if (abilitiesFlag == null) {
            return true;
        }
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        return query.testState(BukkitAdapter.adapt(location), localPlayer, abilitiesFlag);
    }

    /**
     * Checks WorldGuard's own built-in {@code PVP} flag at a location — independent of the
     * {@code aspects-abilities} flag above, which only gates ability activation, not combat itself.
     *
     * <p>Deliberately uses {@code queryState(...) != DENY} rather than {@code testState(...)}: the
     * PVP flag is registered by WorldGuard itself with a {@code false} coded default, which means
     * {@link StateFlag#getDefault()} returns {@code null} ("no decision"), not an explicit ALLOW —
     * and {@code testState}/{@code StateFlag.test(...)} treats a {@code null} result as denied. That
     * would make this method return {@code false} (PvP not allowed) everywhere WorldGuard is
     * installed, for any point not covered by a region that explicitly sets {@code pvp}, which is
     * not what this flag means in practice. WorldGuard's own internal PvP enforcement uses this same
     * {@code != DENY} pattern for exactly that reason, treating "no applicable region" as allowed.
     */
    boolean isPvpAllowed(Location location, Player actor) {
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(actor);
        return query.queryState(BukkitAdapter.adapt(location), localPlayer, Flags.PVP) != StateFlag.State.DENY;
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
                plugin.getLogger().info("Reusing existing '" + FLAG_NAME + "' WorldGuard flag (already registered).");
            } else {
                plugin.getLogger().warning("Could not register the '" + FLAG_NAME +
                        "' WorldGuard flag: " + e.getMessage());
            }
        } catch (IllegalStateException e) {
            plugin.getLogger().warning("Could not register the '" + FLAG_NAME +
                    "' WorldGuard flag because the flag registry is no longer accepting new flags: " +
                    e.getMessage() + ". Region-based ability restrictions will not be available this session.");
        }
    }
}
