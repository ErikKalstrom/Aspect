package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.AbilityContext;
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.util.ParticleUtil;
import com.aspects.smp.util.SoundUtil;
import org.bukkit.entity.Player;

public final class BloodActiveAbility implements Ability {
    public static final String BLOOD_PACT_BUFF_ID = "blood-pact";

    @Override
    public boolean activate(AbilityContext context) {
        Player player = context.player();
        AspectDefinition definition = context.aspect().definition();

        double sacrificeAmount = definition.getDouble("active.sacrifice-amount", 2.0);
        if (player.getHealth() - sacrificeAmount <= 0) {
            return false;
        }

        int buffDurationTicks = definition.getInt("active.buff-duration-ticks", 100);
        long buffDurationMillis = buffDurationTicks * 50L;
        context.plugin().getTemporaryEffectManager()
                .startBuff(player.getUniqueId(), BLOOD_PACT_BUFF_ID, buffDurationMillis);

        player.setHealth(Math.max(0.0, player.getHealth() - sacrificeAmount));

        SoundUtil.play(player, "entity.player.hurt", 1.0f, 0.6f, context.plugin().getLogger());
        ParticleUtil.spawn(player.getLocation(), "DAMAGE_INDICATOR", 15, context.plugin().getLogger());
        return true;
    }
}
