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
     * diagonal cast can't slip through a corner gap too narrow for a player to actually fit through.
     * Note: a 45-degree diagonal hitbox corner actually reaches ~0.42 blocks sideways, slightly more
     * than this; a corner intruding between 0.3 and 0.42 blocks is a known, accepted gap for a
     * teleport (as opposed to physically walked movement, which Minecraft's own collision already
     * governs) -- tightening this further would cost more sampling for a vanishingly narrow case. */
    private static final double HITBOX_HALF_WIDTH = 0.3;

    /** How far below the caster's own height a landing column may drop, instead of requiring the
     * floor to sit at exactly the caster's own feet height. (A step up isn't reachable here: the
     * horizontal path is only ever validated at the caster's own height, so a higher landing would
     * place the player somewhere the dash itself was never actually checked as clear.) */
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
     * Searches downward from the caster's own height for the first solid, non-hazardous floor,
     * instead of requiring the floor to sit at exactly the caster's own feet height -- without this,
     * casting down a single step would always fail even though a safe landing exists just below the
     * sampled point. Scans one block layer at a time and stops the moment a layer isn't passable,
     * exactly like a horizontal wall stops {@link #pickSafeDistance}: a solid or hazardous block
     * blocks any further descent, so this can never return a landing on the far side of one -- the
     * earlier version checked each candidate height's own three blocks independently and could skip
     * straight past an obstruction to a lower, unreachable candidate.
     */
    private OptionalInt findLandingY(AbilityContext context, World world, Location point) {
        int bx = point.getBlockX();
        int bz = point.getBlockZ();
        int top = point.getBlockY();
        int bottom = Math.max(top - MAX_STEP_DOWN, world.getMinHeight() + 1);
        if (top + 1 >= world.getMaxHeight() || !isPassableAt(world, bx, top + 1, bz)) {
            return OptionalInt.empty();
        }
        for (int y = top; y >= bottom; y--) {
            if (!isPassableAt(world, bx, y, bz)) {
                return OptionalInt.empty();
            }
            Material floor = world.getBlockAt(bx, y - 1, bz).getType();
            if (floor.isSolid() && !HAZARD_MATERIALS.contains(floor)) {
                Location candidate = new Location(world, bx + 0.5, y, bz + 0.5);
                if (context.plugin().getProtectionGate().isLocationAllowed(candidate, context.player())) {
                    return OptionalInt.of(y);
                }
                // Real solid ground exists right here but this exact spot is protection-denied -- the
                // player can't physically sink through solid ground to try a lower spot instead, so
                // there is no valid landing in this column at all.
                return OptionalInt.empty();
            }
        }
        return OptionalInt.empty();
    }

    private boolean isPassableAt(World world, int bx, int y, int bz) {
        return isPassable(world.getBlockAt(bx, y, bz).getType());
    }

    private static boolean isPassable(Material material) {
        return !material.isSolid() && !HAZARD_MATERIALS.contains(material);
    }
}
