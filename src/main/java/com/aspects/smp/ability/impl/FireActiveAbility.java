package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.AbilityContext;
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.util.ParticleUtil;
import com.aspects.smp.util.SoundUtil;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class FireActiveAbility implements Ability {
    @Override
    public boolean activate(AbilityContext context) {
        Player caster = context.player();
        AspectDefinition definition = context.aspect().definition();

        double radius = definition.getDouble("active.radius", 4.0);
        double damage = definition.getDouble("active.damage", 4.0);
        int burnTicks = definition.getInt("active.burn-ticks", 60);
        int windupTicks = definition.getInt("active.windup-ticks", 10);

        SoundUtil.play(caster, "entity.blaze.shoot", 1.0f, 1.0f, context.plugin().getLogger());
        ParticleUtil.spawn(caster.getLocation(), "FLAME", 15, context.plugin().getLogger());

        Location origin = caster.getLocation();
        context.plugin().getServer().getScheduler().runTaskLater(context.plugin(), () -> {
            if (!caster.isOnline()) {
                return;
            }
            List<Entity> nearby = new ArrayList<>(caster.getWorld().getNearbyEntities(origin, radius, radius, radius));
            for (Entity entity : nearby) {
                if (!(entity instanceof Player target) || target.getUniqueId().equals(caster.getUniqueId())) {
                    continue;
                }
                if (target.getLocation().distance(origin) > radius) {
                    continue;
                }
                if (!context.plugin().getProtectionGate().canTargetPlayer(caster, target)) {
                    continue;
                }
                double healthBefore = target.getHealth();
                target.damage(damage, caster);
                if (target.getHealth() < healthBefore) {
                    target.setFireTicks(Math.max(target.getFireTicks(), burnTicks));
                }
            }
            SoundUtil.play(caster, "entity.generic.explode", 1.0f, 1.0f, context.plugin().getLogger());
            ParticleUtil.spawn(origin, "LAVA", 25, context.plugin().getLogger());
        }, windupTicks);

        return true;
    }
}
