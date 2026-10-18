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
import java.util.OptionalInt;
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

    /** Half the player hitbox width (~0.6 wide), checked either side of the path's center line so a
     * diagonal cast can't slip through a corner gap too narrow for a player to actually fit through. */
    private static final double HITBOX_HALF_WIDTH = 0.3;

    /** How far up/down from the caster's own height a landing column may be, closest-to-level first:
     * accepts stepping up one block or down up to three, instead of requiring perfectly flat ground. */
    private static final int MAX_STEP_UP = 1;
    private static final int MAX_STEP_DOWN = 3;

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

        Location landingPoint = origin.clone().add(horizontal.clone().multiply(safeDistance));
        OptionalInt landingY = findLandingY(context, world, landingPoint);
        if (landingY.isEmpty()) {
            return false;
        }

        double destX = Math.floor(landingPoint.getX()) + 0.5;
        double destZ = Math.floor(landingPoint.getZ()) + 0.5;
        Location destination = new Location(world, destX, landingY.getAsInt(), destZ, origin.getYaw(), origin.getPitch());

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
        Vector perpendicular = new Vector(-horizontal.getZ(), 0, horizontal.getX()).multiply(HITBOX_HALF_WIDTH);
        for (double step = distance - 1 + PATH_SAMPLE_STEP; step < distance + 1e-9; step += PATH_SAMPLE_STEP) {
            Location center = origin.clone().add(horizontal.clone().multiply(step));
            if (!isColumnClear(world, center)
                    || !isColumnClear(world, center.clone().add(perpendicular))
                    || !isColumnClear(world, center.clone().subtract(perpendicular))) {
                return false;
            }
        }
        return true;
    }

    private boolean isColumnClear(World world, Location point) {
        int y = point.getBlockY();
        int bx = point.getBlockX();
        int bz = point.getBlockZ();
        Material feet = world.getBlockAt(bx, y, bz).getType();
        Material head = world.getBlockAt(bx, y + 1, bz).getType();
        return isPassable(feet) && isPassable(head);
    }

    private boolean isValidLandingAtDistance(AbilityContext context, World world, Location origin, Vector horizontal, int distance) {
        Location point = origin.clone().add(horizontal.clone().multiply(distance));
        if (!world.getWorldBorder().isInside(point)) {
            return false;
        }
        return findLandingY(context, world, point).isPresent();
    }

    /**
     * Searches for solid, non-hazardous footing near the caster's own height at a candidate column,
     * closest-to-level first (one block up, then level, then up to three blocks down) instead of
     * requiring the floor to sit at exactly the caster's own feet height. Without this, casting
     * mid-jump, down a single step, or over a slab/stair would always fail even though a safe
     * landing exists just above or below the sampled point.
     */
    private OptionalInt findLandingY(AbilityContext context, World world, Location point) {
        int bx = point.getBlockX();
        int bz = point.getBlockZ();
        int originY = point.getBlockY();
        for (int offset = MAX_STEP_UP; offset >= -MAX_STEP_DOWN; offset--) {
            int y = originY + offset;
            if (y - 1 < world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
                continue;
            }
            if (isSafeLandingColumn(world, bx, y, bz)) {
                Location candidate = new Location(world, bx + 0.5, y, bz + 0.5);
                if (context.plugin().getProtectionGate().isLocationAllowed(candidate, context.player())) {
                    return OptionalInt.of(y);
                }
            }
        }
        return OptionalInt.empty();
    }

    private boolean isSafeLandingColumn(World world, int bx, int y, int bz) {
        Material feet = world.getBlockAt(bx, y, bz).getType();
        Material head = world.getBlockAt(bx, y + 1, bz).getType();
        Material floor = world.getBlockAt(bx, y - 1, bz).getType();
        return isSafeLanding(feet, head, floor);
    }

    /**
     * Pure landing-safety decision, extracted so it can be unit-tested without a live Bukkit World:
     * feet/head must be passable (not solid, not a hazard like fire or powder snow), and the floor
     * must be real solid, non-hazardous ground -- never open air, a cliff edge, or the void itself.
     */
    static boolean isSafeLanding(Material feet, Material head, Material floor) {
        return isPassable(feet) && isPassable(head) && floor.isSolid() && !HAZARD_MATERIALS.contains(floor);
    }

    private static boolean isPassable(Material material) {
        return !material.isSolid() && !HAZARD_MATERIALS.contains(material);
    }
}
