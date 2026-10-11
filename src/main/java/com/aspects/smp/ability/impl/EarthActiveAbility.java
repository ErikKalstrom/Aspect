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
        // addPotionEffect replaces the passive's infinite-duration Resistance I outright, so once
        // this timed Resistance II expires the player would have no resistance at all (not back to
        // Resistance I) until their next equip/respawn. Re-apply the passive once the buff ends.
        context.plugin().getServer().getScheduler().runTaskLater(context.plugin(), () -> {
            if (player.isOnline()) {
                context.plugin().getAspectManager().reapplyPassive(player);
            }
        }, duration);

        SoundUtil.play(player, "block.stone.break", 1.0f, 0.8f, context.plugin().getLogger());
        ParticleUtil.spawn(player.getLocation(), "CRIT", 20, context.plugin().getLogger());
        return true;
    }
}
