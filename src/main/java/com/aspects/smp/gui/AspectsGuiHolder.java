package com.aspects.smp.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

public interface AspectsGuiHolder extends InventoryHolder {
    void onClick(InventoryClickEvent event);

    void onClose(InventoryCloseEvent event);
}
