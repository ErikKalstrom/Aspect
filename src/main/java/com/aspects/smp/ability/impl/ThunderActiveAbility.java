package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.AbilityContext;
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.util.DirectionUtil;
import com.aspects.smp.util.ParticleUtil;
import com.aspects.smp.util.SoundUtil;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class ThunderActiveAbility implements Ability {
    @Override
    public boolean activate(AbilityContext context) {
        Player player = context.player();
        AspectDefinition definition = context.aspect().definition();

        double maxDistance = definition.getDouble("active.max-distance", 7.0);
        double velocityPerBlock = definition.getDouble("active.velocity-per-block", 0.6);
        double verticalBoost = definition.getDouble("active.vertical-boost", 0.15);
        double safetyMargin = definition.getDouble("active.wall-safety-margin", 0.5);

        Vector horizontal = DirectionUtil.horizontalFromYaw(player.getLocation().getYaw());
        Location eyeLocation = player.getEyeLocation();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(
                eyeLocation, horizontal, maxDistance, FluidCollisionMode.NEVER, true);
        Double hitDistance = hit == null ? null : eyeLocation.toVector().distance(hit.getHitPosition());

        double distance = effectiveDistance(hitDistance, maxDistance, safetyMargin);

        Vector impulse = horizontal.multiply(distance * velocityPerBlock).setY(verticalBoost);
        player.setVelocity(impulse);

        SoundUtil.play(player, "entity.lightning_bolt.impact", 1.0f, 1.2f, context.plugin().getLogger());
        ParticleUtil.spawn(player.getLocation(), "ELECTRIC_SPARK", 25, context.plugin().getLogger());
        return true;
    }

    static double effectiveDistance(Double hitDistance, double maxDistance, double safetyMargin) {
        if (hitDistance == null) {
            return maxDistance;
        }
        return Math.max(0.0, Math.min(maxDistance, hitDistance - safetyMargin));
    }
}
