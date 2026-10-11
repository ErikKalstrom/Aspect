package com.aspects.smp.ability;

import com.aspects.smp.aspect.AspectDefinition;
import org.bukkit.entity.Player;

public interface PassiveAbility {
    void onEquip(Player player, AspectDefinition definition);

    void onUnequip(Player player);
}
