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

        IntPredicate isSafeAtDistance = distance -> isSafeDestination(world, origin, horizontal, distance);
        int safeDistance = pickSafeDistance(maxDistance, isSafeAtDistance);
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

    static int pickSafeDistance(int maxDistance, IntPredicate isSafeAtDistance) {
        for (int distance = maxDistance; distance >= 1; distance--) {
            if (isSafeAtDistance.test(distance)) {
                return distance;
            }
        }
        return 0;
    }

    private boolean isSafeDestination(World world, Location origin, Vector horizontal, int distance) {
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
        return !feet.isSolid() && !head.isSolid() && feet != Material.LAVA && head != Material.LAVA
                && floor != Material.LAVA;
    }
}
