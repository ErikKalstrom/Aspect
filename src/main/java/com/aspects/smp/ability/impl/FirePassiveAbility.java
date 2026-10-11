package com.aspects.smp.ability.impl;

import com.aspects.smp.ability.PassiveAbility;
import com.aspects.smp.aspect.AspectDefinition;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class FirePassiveAbility implements PassiveAbility {
    @Override
    public void onEquip(Player player, AspectDefinition definition) {
        if (!definition.getBoolean("passive.fire-resistance", true)) {
            return;
        }
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, true, false, false));
    }

    @Override
    public void onUnequip(Player player) {
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
    }
}
