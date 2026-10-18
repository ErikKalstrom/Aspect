package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.AbilityContext;
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.util.DirectionUtil;
import com.aspects.smp.util.ParticleUtil;
import com.aspects.smp.util.SoundUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.IntPredicate;

public final class VoidActiveAbility implements Ability {
    /** Blocks that are either not solid but still dangerous to stand in, or solid but still hazardous underfoot. */
    private static final Set<Material> HAZARD_MATERIALS = EnumSet.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.MAGMA_BLOCK,
            Material.POWDER_SNOW, Material.COBWEB);

    /** Sub-block sampling resolution along the path, so a diagonal cast can't clip through a block corner
     * that falls between two whole-block lattice points. */
    private static final double PATH_SAMPLE_STEP = 0.25;

    @Override
    public boolean activate(AbilityContext context) {
        Player player = context.player();
        AspectDefinition definition = context.aspect().definition();

        int maxDistance = (int) definition.getDouble("active.max-distance", 7.0);

        Location origin = player.getLocation();
        World world = player.getWorld();
        Vector horizontal = DirectionUtil.horizontalFromYaw(origin.getYaw());

        IntPredicate isPathClear = distance -> isPathClearAtDistance(world, origin, horizontal, distance);
        IntPredicate isValidLanding = distance -> isValidLandingAtDistance(context, world, origin, horizontal, distance);
        int safeDistance = pickSafeDistance(maxDistance, isPathClear, isValidLanding);
        if (safeDistance == 0) {
            return false;
        }

        double destX = Math.floor(origin.getX() + horizontal.getX() * safeDistance) + 0.5;
        double destY = Math.floor(origin.getY() + horizontal.getY() * safeDistance);
        double destZ = Math.floor(origin.getZ() + horizontal.getZ() * safeDistance) + 0.5;
        Location destination = new Location(world, destX, destY, destZ, origin.getYaw(), origin.getPitch());

        ParticleUtil.spawn(origin, "PORTAL", 30, context.plugin().getLogger());
        boolean teleported = player.teleport(destination);
        if (!teleported) {
            return false;
        }
        ParticleUtil.spawn(destination, "PORTAL", 30, context.plugin().getLogger());
        SoundUtil.play(player, "entity.enderman.teleport", 1.0f, 1.0f, context.plugin().getLogger());
        return true;
    }

    /**
     * Walks outward one block at a time so a solid wall partway along the path stops the search from ever
     * considering a square beyond it -- picking the farthest safe landing found before any such wall is hit.
     * This is what makes Void Step unable to clip through a blocked wall, per spec: a destination beyond an
     * obstruction is simply never reachable in a straight line, so it's never even evaluated as a landing spot.
     */
    static int pickSafeDistance(int maxDistance, IntPredicate isPathClear, IntPredicate isValidLanding) {
        int best = 0;
        for (int distance = 1; distance <= maxDistance; distance++) {
            if (!isPathClear.test(distance)) {
                break;
            }
            if (isValidLanding.test(distance)) {
                best = distance;
            }
        }
        return best;
    }

    private boolean isPathClearAtDistance(World world, Location origin, Vector horizontal, int distance) {
        for (double step = distance - 1 + PATH_SAMPLE_STEP; step < distance + 1e-9; step += PATH_SAMPLE_STEP) {
            if (!isPointClear(world, origin, horizontal, step)) {
                return false;
            }
        }
        return true;
    }

    private boolean isPointClear(World world, Location origin, Vector horizontal, double distance) {
        Location point = origin.clone().add(horizontal.clone().multiply(distance));
        int y = point.getBlockY();
        int bx = point.getBlockX();
        int bz = point.getBlockZ();
        Material feet = world.getBlockAt(bx, y, bz).getType();
        Material head = world.getBlockAt(bx, y + 1, bz).getType();
        return !feet.isSolid() && !head.isSolid() && !HAZARD_MATERIALS.contains(feet) && !HAZARD_MATERIALS.contains(head);
    }

    private boolean isValidLandingAtDistance(AbilityContext context, World world, Location origin, Vector horizontal, int distance) {
        Location point = origin.clone().add(horizontal.clone().multiply(distance));
        if (!world.getWorldBorder().isInside(point)) {
            return false;
        }
        int y = point.getBlockY();
        if (y - 1 < world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
            return false;
        }
        int bx = point.getBlockX();
        int bz = point.getBlockZ();
        Material feet = world.getBlockAt(bx, y, bz).getType();
        Material head = world.getBlockAt(bx, y + 1, bz).getType();
        Material floor = world.getBlockAt(bx, y - 1, bz).getType();
        if (feet.isSolid() || head.isSolid() || HAZARD_MATERIALS.contains(feet) || HAZARD_MATERIALS.contains(head)) {
            return false;
        }
        // Require real solid ground to land on -- otherwise the destination is open air, a cliff edge,
        // or the void itself, none of which the spec allows ("never teleport into ... the void").
        if (!floor.isSolid() || HAZARD_MATERIALS.contains(floor)) {
            return false;
        }
        return context.plugin().getProtectionGate().isLocationAllowed(point, context.player());
    }
}
