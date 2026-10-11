package com.aspects.smp.item;

import com.aspects.smp.AspectsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

public final class RerollerProtectionListener implements Listener {
    private final AspectsPlugin plugin;

    public RerollerProtectionListener(AspectsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof CraftingRecipe craftingRecipe) || isTheRerollerRecipe(craftingRecipe)) {
            return;
        }
        for (ItemStack matrixItem : event.getInventory().getMatrix()) {
            if (plugin.getCustomItemManager().isReroller(matrixItem)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (plugin.getCustomItemManager().isReroller(item)) {
            event.setCancelled(true);
        }
    }

    private boolean isTheRerollerRecipe(CraftingRecipe recipe) {
        return recipe.getKey().equals(plugin.getRerollerRecipeManager().recipeKey());
    }
}
