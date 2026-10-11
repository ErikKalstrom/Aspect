package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.AbilityContext;
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.util.ParticleUtil;
import com.aspects.smp.util.SoundUtil;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

public final class TideActiveAbility implements Ability {
    @Override
    public boolean activate(AbilityContext context) {
        Player player = context.player();
        AspectDefinition definition = context.aspect().definition();
        double healAmount = definition.getDouble("active.heal-amount", 4.0);

        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        double newHealth = Math.min(maxHealth, player.getHealth() + healAmount);
        player.setHealth(newHealth);

        SoundUtil.play(player, "entity.generic.drink", 1.0f, 1.0f, context.plugin().getLogger());
        ParticleUtil.spawn(player.getLocation(), "DRIPPING_WATER", 20, context.plugin().getLogger());
        return true;
    }
}
