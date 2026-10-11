package com.aspects.smp.config;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public record RecipesConfig(
        boolean rerollerEnabled,
        List<String> rerollerPattern,
        Map<Character, Material> rerollerIngredients,
        int rerollerResultAmount
) {
}
