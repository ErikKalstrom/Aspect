package com.aspects.smp;

import org.bukkit.plugin.java.JavaPlugin;

public final class AspectsPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Aspects scaffold enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Aspects disabled.");
    }
}
