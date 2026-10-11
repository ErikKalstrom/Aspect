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

        // Verified on a live 1.21.11 server: applying a higher-amplifier Resistance on top of the
        // passive's infinite-duration Resistance I stores the old one as a vanilla "hidden effect"
        // (visible in the entity's active_effects NBT) and restores it automatically the instant this
        // timed effect ends — no manual re-application needed.
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, amplifier, true, true, true));

        SoundUtil.play(player, "block.stone.break", 1.0f, 0.8f, context.plugin().getLogger());
        ParticleUtil.spawn(player.getLocation(), "CRIT", 20, context.plugin().getLogger());
        return true;
    }
}
