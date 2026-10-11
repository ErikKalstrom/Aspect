package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.PassiveAbility;
import com.aspects.smp.aspect.AspectDefinition;
import org.bukkit.entity.Player;

public final class VoidPassiveAbility implements PassiveAbility {
    @Override
    public void onEquip(Player player, AspectDefinition definition) {
        // Fall-damage reduction has no vanilla potion-effect equivalent. It is enforced
        // live by PlayerDamageListener checking the player's current Aspect on each FALL
        // damage event, so there is no state to apply here.
    }

    @Override
    public void onUnequip(Player player) {
        // No state to remove — see onEquip.
    }
}
