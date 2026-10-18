package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.AbilityContext;
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.util.ParticleUtil;
import com.aspects.smp.util.SoundUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public final class FrostActiveAbility implements Ability {
    @Override
    public boolean activate(AbilityContext context) {
        Player caster = context.player();
        AspectDefinition definition = context.aspect().definition();

        double radius = definition.getDouble("active.radius", 5.0);
        int slownessDuration = definition.getInt("active.slowness-duration-ticks", 60);
        int slownessAmplifier = definition.getInt("active.slowness-amplifier", 1);
        boolean miningFatigueEnabled = definition.getBoolean("active.mining-fatigue-enabled", false);
        int miningFatigueTicks = definition.getInt("active.mining-fatigue-ticks", 40);

        List<Entity> nearby = new ArrayList<>(caster.getWorld().getNearbyEntities(
                caster.getLocation(), radius, radius, radius));
        for (Entity entity : nearby) {
            if (!(entity instanceof Player target) || target.getUniqueId().equals(caster.getUniqueId())) {
                continue;
            }
            if (target.getLocation().distance(caster.getLocation()) > radius) {
                continue;
            }
            if (!context.plugin().getProtectionGate().canTargetPlayer(caster, target)) {
                continue;
            }
            target.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, slownessDuration, slownessAmplifier, true, true, true));
            if (miningFatigueEnabled) {
                target.addPotionEffect(new PotionEffect(
                        PotionEffectType.MINING_FATIGUE, miningFatigueTicks, 0, true, true, true));
            }
        }

        SoundUtil.play(caster, "block.glass.break", 1.0f, 1.2f, context.plugin().getLogger());
        ParticleUtil.spawn(caster.getLocation(), "SNOWFLAKE", 30, context.plugin().getLogger());
        return true;
    }
}
