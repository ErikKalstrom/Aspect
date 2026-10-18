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
     * Checks WorldGuard's own built-in {@code PVP} flag at both the caster's and the target's
     * location — independent of the {@code aspects-abilities} flag above, which only gates ability
     * activation, not combat itself. Mirrors WorldGuard's own internal PvP enforcement
     * ({@code RegionProtectionListener}) exactly: both locations must resolve to "not DENY", and the
     * target's location must additionally pass {@code testBuild}, which is what actually accounts for
     * region membership (a non-member is blocked in a region that doesn't explicitly set {@code pvp}
     * at all, not just one that explicitly denies it).
     *
     * <p>The two {@code queryState(...) != DENY} checks are deliberate, not {@code testState(...)}:
     * the PVP flag is registered by WorldGuard itself with a {@code false} coded default, which means
     * {@link StateFlag#getDefault()} returns {@code null} ("no decision"), not an explicit ALLOW — and
     * {@code testState}/{@code StateFlag.test(...)} treats a {@code null} result as denied. That would
     * make this method return {@code false} (PvP not allowed) everywhere WorldGuard is installed, for
     * any point not covered by a region that explicitly sets {@code pvp}, which is not what this flag
     * means in practice.
     */
    boolean isPvpAllowed(Location casterLocation, Location targetLocation, Player caster) {
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(caster);
        com.sk89q.worldedit.util.Location adaptedCaster = BukkitAdapter.adapt(casterLocation);
        com.sk89q.worldedit.util.Location adaptedTarget = BukkitAdapter.adapt(targetLocation);
        if (query.queryState(adaptedCaster, localPlayer, Flags.PVP) == StateFlag.State.DENY) {
            return false;
        }
        if (query.queryState(adaptedTarget, localPlayer, Flags.PVP) == StateFlag.State.DENY) {
            return false;
        }
        return query.testBuild(adaptedTarget, localPlayer, Flags.PVP);
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
