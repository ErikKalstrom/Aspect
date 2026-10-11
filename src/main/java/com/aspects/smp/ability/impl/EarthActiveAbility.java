package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.AbilityContext;
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.util.ParticleUtil;
import com.aspects.smp.util.SoundUtil;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class EarthActiveAbility implements Ability {
    @Override
    public boolean activate(AbilityContext context) {
        Player player = context.player();
        AspectDefinition definition = context.aspect().definition();

        int duration = definition.getInt("active.resistance-duration-ticks", 60);
        int amplifier = definition.getInt("active.resistance-amplifier", 1);

        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, amplifier, true, true, true));

        SoundUtil.play(player, "block.stone.break", 1.0f, 0.8f, context.plugin().getLogger());
        ParticleUtil.spawn(player.getLocation(), "CRIT", 20, context.plugin().getLogger());
        return true;
    }
}
