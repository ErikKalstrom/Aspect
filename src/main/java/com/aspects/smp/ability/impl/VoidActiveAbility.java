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

        Location destination = origin.clone().add(horizontal.clone().multiply(safeDistance));
        destination.setYaw(origin.getYaw());
        destination.setPitch(origin.getPitch());

        ParticleUtil.spawn(origin, "PORTAL", 30, context.plugin().getLogger());
        player.teleport(destination);
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
        if (y < world.getMinHeight() || y + 1 > world.getMaxHeight()) {
            return false;
        }
        Material feet = point.getBlock().getType();
        Material head = point.clone().add(0, 1, 0).getBlock().getType();
        Material floor = point.clone().add(0, -1, 0).getBlock().getType();
        return !feet.isSolid() && !head.isSolid() && feet != Material.LAVA && head != Material.LAVA
                && floor != Material.LAVA;
    }
}
