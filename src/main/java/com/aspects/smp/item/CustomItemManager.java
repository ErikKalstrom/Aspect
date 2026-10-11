package com.aspects.smp.item;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.util.MessageUtil;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

public final class CustomItemManager {
    private final AspectsPlugin plugin;
    private final NamespacedKey rerollerKey;
    private final NamespacedKey selectorSpecificKey;
    private final NamespacedKey selectorGenericKey;

    public CustomItemManager(AspectsPlugin plugin) {
        this.plugin = plugin;
        this.rerollerKey = new NamespacedKey(plugin, "reroller");
        this.selectorSpecificKey = new NamespacedKey(plugin, "selector-specific");
        this.selectorGenericKey = new NamespacedKey(plugin, "selector-generic");
    }

    public ItemStack createReroller() {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(RerollerItem.DISPLAY_NAME).decoration(TextDecoration.ITALIC, false));
        meta.lore(RerollerItem.LORE.stream()
                .map(line -> MessageUtil.parse(line).decoration(TextDecoration.ITALIC, false))
                .toList());
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(rerollerKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isReroller(ItemStack item) {
        if (item == null || item.getType() != Material.AMETHYST_SHARD || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        Boolean tagged = meta.getPersistentDataContainer().get(rerollerKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(tagged);
    }

    public ItemStack createSelectorToken(String aspectId) {
        AspectDefinition definition = plugin.getConfigManager().aspectDefinitions().get(aspectId);
        String targetDisplayName = definition != null ? definition.displayName() : aspectId;

        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(AspectSelectorItem.TOKEN_DISPLAY_NAME).decoration(TextDecoration.ITALIC, false));
        meta.lore(AspectSelectorItem.tokenLore(targetDisplayName).stream()
                .map(line -> MessageUtil.parse(line).decoration(TextDecoration.ITALIC, false))
                .toList());
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(selectorSpecificKey, PersistentDataType.STRING, aspectId);
        item.setItemMeta(meta);
        return item;
    }

    public Optional<String> getSelectorTokenAspectId(ItemStack item) {
        if (item == null || item.getType() != Material.ENDER_EYE || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String aspectId = item.getItemMeta().getPersistentDataContainer().get(selectorSpecificKey, PersistentDataType.STRING);
        return Optional.ofNullable(aspectId);
    }

    public ItemStack createGenericSelector() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(AspectSelectorItem.SELECTOR_DISPLAY_NAME).decoration(TextDecoration.ITALIC, false));
        meta.lore(AspectSelectorItem.SELECTOR_LORE.stream()
                .map(line -> MessageUtil.parse(line).decoration(TextDecoration.ITALIC, false))
                .toList());
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(selectorGenericKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isGenericSelector(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) {
            return false;
        }
        Boolean tagged = item.getItemMeta().getPersistentDataContainer().get(selectorGenericKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(tagged);
    }
}
