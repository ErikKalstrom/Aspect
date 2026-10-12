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

import java.util.function.IntPredicate;

public final class VoidActiveAbility implements Ability {
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
        Location point = origin.clone().add(horizontal.clone().multiply(distance));
        int y = point.getBlockY();
        int bx = point.getBlockX();
        int bz = point.getBlockZ();
        Material feet = world.getBlockAt(bx, y, bz).getType();
        Material head = world.getBlockAt(bx, y + 1, bz).getType();
        return !feet.isSolid() && !head.isSolid();
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
        if (feet.isSolid() || head.isSolid() || feet == Material.LAVA || head == Material.LAVA || floor == Material.LAVA) {
            return false;
        }
        return context.plugin().getProtectionGate().isLocationAllowed(point, context.player());
    }
}
