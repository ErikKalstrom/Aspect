package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.AbilityContext;
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.util.ParticleUtil;
import com.aspects.smp.util.SoundUtil;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class WindActiveAbility implements Ability {
    @Override
    public boolean activate(AbilityContext context) {
        Player player = context.player();
        AspectDefinition definition = context.aspect().definition();

        double upwardVelocity = definition.getDouble("active.upward-velocity", 1.0);
        double forwardVelocity = definition.getDouble("active.forward-velocity", 0.6);

        Vector horizontal = horizontalFromYaw(player.getLocation().getYaw());
        Vector impulse = horizontal.multiply(forwardVelocity).setY(upwardVelocity);
        player.setVelocity(impulse);

        SoundUtil.play(player, "entity.bat.takeoff", 1.0f, 1.0f, context.plugin().getLogger());
        ParticleUtil.spawn(player.getLocation(), "CLOUD", 20, context.plugin().getLogger());
        return true;
    }

    static Vector horizontalFromYaw(float yawDegrees) {
        double yawRadians = Math.toRadians(yawDegrees);
        return new Vector(-Math.sin(yawRadians), 0, Math.cos(yawRadians));
    }
}
