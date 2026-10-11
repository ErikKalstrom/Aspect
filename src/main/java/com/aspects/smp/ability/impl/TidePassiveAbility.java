package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.PassiveAbility;
import com.aspects.smp.aspect.AspectDefinition;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class TidePassiveAbility implements PassiveAbility {
    @Override
    public void onEquip(Player player, AspectDefinition definition) {
        if (!definition.getBoolean("passive.water-breathing", true)) {
            return;
        }
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.WATER_BREATHING, PotionEffect.INFINITE_DURATION, 0, true, false, false));
    }

    @Override
    public void onUnequip(Player player) {
        player.removePotionEffect(PotionEffectType.WATER_BREATHING);
    }
}
