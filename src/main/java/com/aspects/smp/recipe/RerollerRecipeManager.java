package com.aspects.smp.recipe;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.config.RecipesConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

import java.util.Map;

public final class RerollerRecipeManager {
    private static final String RECIPE_KEY = "reroller_recipe";

    private final AspectsPlugin plugin;

    public RerollerRecipeManager(AspectsPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(RecipesConfig config) {
        NamespacedKey key = new NamespacedKey(plugin, RECIPE_KEY);
        Bukkit.removeRecipe(key);
        if (!config.rerollerEnabled()) {
            return;
        }
        ItemStack result = plugin.getCustomItemManager().createReroller();
        result.setAmount(config.rerollerResultAmount());
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(config.rerollerPattern().toArray(new String[0]));
        for (Map.Entry<Character, Material> entry : config.rerollerIngredients().entrySet()) {
            recipe.setIngredient(entry.getKey(), entry.getValue());
        }
        Bukkit.addRecipe(recipe);
    }
}
