package com.aspects.smp.item;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.util.MessageUtil;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class CustomItemManager {
    private final NamespacedKey rerollerKey;

    public CustomItemManager(AspectsPlugin plugin) {
        this.rerollerKey = new NamespacedKey(plugin, "reroller");
    }

    public ItemStack createReroller() {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.parse(RerollerItem.DISPLAY_NAME).decoration(TextDecoration.ITALIC, false));
        meta.lore(RerollerItem.LORE.stream()
                .map(line -> MessageUtil.parse(line).decoration(TextDecoration.ITALIC, false))
                .toList());
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
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
}
