package com.aspects.smp.ability;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.Aspect;
import org.bukkit.entity.Player;

public record AbilityContext(Player player, Aspect aspect, AspectsPlugin plugin) {
}
