package com.aspects.smp;

import com.aspects.smp.ability.AbilityCooldownManager;
import com.aspects.smp.ability.AbilityManager;
import com.aspects.smp.ability.ActivationListener;
import com.aspects.smp.ability.ProtectionGate;
import com.aspects.smp.ability.impl.FireActiveAbility;
import com.aspects.smp.ability.impl.FirePassiveAbility;
import com.aspects.smp.ability.impl.TideActiveAbility;
import com.aspects.smp.ability.impl.TidePassiveAbility;
import com.aspects.smp.aspect.Aspect;
import com.aspects.smp.aspect.AspectAssignmentService;
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.aspect.AspectManager;
import com.aspects.smp.aspect.AspectRegistry;
import com.aspects.smp.command.AdminCommand;
import com.aspects.smp.command.AspectsCommand;
import com.aspects.smp.command.AspectsTabCompleter;
import com.aspects.smp.config.ConfigManager;
import com.aspects.smp.config.ConfigValidationException;
import com.aspects.smp.data.PlayerDataRepository;
import com.aspects.smp.data.SQLitePlayerDataRepository;
import com.aspects.smp.listener.PlayerJoinListener;
import com.aspects.smp.listener.PlayerQuitListener;
import com.aspects.smp.listener.PlayerRespawnListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;

public final class AspectsPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private PlayerDataRepository dataRepository;
    private AspectRegistry aspectRegistry;
    private AspectManager aspectManager;
    private AbilityCooldownManager cooldownManager;
    private AbilityManager abilityManager;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        try {
            configManager.loadAll();
        } catch (ConfigValidationException e) {
            getLogger().severe("Failed to load configuration, disabling plugin:");
            e.errors().forEach(error -> getLogger().severe(" - " + error));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        dataRepository = new SQLitePlayerDataRepository(
                new File(getDataFolder(), configManager.general().sqliteFile()), getLogger());

        aspectRegistry = buildRegistry(configManager.aspectDefinitions());

        cooldownManager = new AbilityCooldownManager(dataRepository);
        aspectManager = new AspectManager(aspectRegistry, new AspectAssignmentService(), dataRepository,
                cooldownManager, getLogger(), configManager.general().excludeCurrentOnReroll(),
                runnable -> getServer().getScheduler().runTask(this, runnable));

        ProtectionGate protectionGate = new ProtectionGate(configManager.general().blockedWorlds());
        abilityManager = new AbilityManager(aspectManager, cooldownManager, protectionGate, this);

        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new ActivationListener(abilityManager, this), this);

        AdminCommand adminCommand = new AdminCommand(this);
        AspectsCommand aspectsCommand = new AspectsCommand(this, adminCommand);
        AspectsTabCompleter tabCompleter = new AspectsTabCompleter(this);
        PluginCommand command = getCommand("aspects");
        if (command != null) {
            command.setExecutor(aspectsCommand);
            command.setTabCompleter(tabCompleter);
        }

        getLogger().info("Aspects enabled with " + aspectRegistry.enabled().size() + " enabled aspect(s).");
    }

    @Override
    public void onDisable() {
        if (dataRepository != null) {
            dataRepository.close();
        }
    }

    private AspectRegistry buildRegistry(Map<String, AspectDefinition> definitions) {
        AspectRegistry registry = new AspectRegistry();

        AspectDefinition fire = definitions.get("fire");
        if (fire != null) {
            registry.register(new Aspect(fire, new FirePassiveAbility(), new FireActiveAbility()));
        }
        AspectDefinition tide = definitions.get("tide");
        if (tide != null) {
            registry.register(new Aspect(tide, new TidePassiveAbility(), new TideActiveAbility()));
        }

        for (AspectDefinition definition : definitions.values()) {
            if (!definition.id().equals("fire") && !definition.id().equals("tide")) {
                getLogger().info("Aspect '" + definition.id() +
                        "' is defined in aspects.yml but not yet implemented in code; skipping registration.");
            }
        }
        return registry;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public AspectManager getAspectManager() {
        return aspectManager;
    }
}
