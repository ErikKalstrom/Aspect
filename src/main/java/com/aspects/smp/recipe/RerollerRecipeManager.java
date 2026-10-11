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
        try {
            ItemStack result = plugin.getCustomItemManager().createReroller();
            result.setAmount(config.rerollerResultAmount());
            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape(config.rerollerPattern().toArray(new String[0]));
            for (Map.Entry<Character, Material> entry : config.rerollerIngredients().entrySet()) {
                recipe.setIngredient(entry.getKey(), entry.getValue());
            }
            Bukkit.addRecipe(recipe);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().severe("Failed to register the Reroller crafting recipe from recipes.yml: " +
                    e.getMessage() + " — the Reroller will not be craftable until this is fixed.");
        }
    }

    public NamespacedKey recipeKey() {
        return new NamespacedKey(plugin, RECIPE_KEY);
    }
}
