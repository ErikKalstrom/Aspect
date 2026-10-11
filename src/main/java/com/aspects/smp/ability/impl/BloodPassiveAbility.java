package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.PassiveAbility;
import com.aspects.smp.aspect.AspectDefinition;
import org.bukkit.entity.Player;

public final class BloodPassiveAbility implements PassiveAbility {
    @Override
    public void onEquip(Player player, AspectDefinition definition) {
        // The melee-damage bonus has no vanilla potion-effect equivalent. It is enforced
        // live by PlayerDamageListener checking the damaging player's current Aspect on
        // each EntityDamageByEntityEvent, so there is no state to apply here.
    }

    @Override
    public void onUnequip(Player player) {
        // No state to remove — see onEquip.
    }
}
