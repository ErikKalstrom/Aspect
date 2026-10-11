package com.aspects.smp;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.AbilityCooldownManager;
import com.aspects.smp.ability.AbilityManager;
import com.aspects.smp.ability.ActivationListener;
import com.aspects.smp.ability.PassiveAbility;
import com.aspects.smp.ability.ProtectionGate;
import com.aspects.smp.ability.impl.EarthActiveAbility;
import com.aspects.smp.ability.impl.EarthPassiveAbility;
import com.aspects.smp.ability.impl.FireActiveAbility;
import com.aspects.smp.ability.impl.FirePassiveAbility;
import com.aspects.smp.ability.impl.FrostActiveAbility;
import com.aspects.smp.ability.impl.FrostPassiveAbility;
import com.aspects.smp.ability.impl.ThunderActiveAbility;
import com.aspects.smp.ability.impl.ThunderPassiveAbility;
import com.aspects.smp.ability.impl.TideActiveAbility;
import com.aspects.smp.ability.impl.TidePassiveAbility;
import com.aspects.smp.ability.impl.WindActiveAbility;
import com.aspects.smp.ability.impl.WindPassiveAbility;
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
import com.aspects.smp.listener.PlayerDamageListener;
import com.aspects.smp.listener.PlayerJoinListener;
import com.aspects.smp.listener.PlayerQuitListener;
import com.aspects.smp.listener.PlayerRespawnListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.function.Supplier;

public final class AspectsPlugin extends JavaPlugin {
    private static final Map<String, Supplier<PassiveAbility>> PASSIVE_FACTORIES = Map.of(
            "fire", FirePassiveAbility::new,
            "tide", TidePassiveAbility::new,
            "frost", FrostPassiveAbility::new,
            "wind", WindPassiveAbility::new,
            "earth", EarthPassiveAbility::new,
            "thunder", ThunderPassiveAbility::new
    );
    private static final Map<String, Supplier<Ability>> ACTIVE_FACTORIES = Map.of(
            "fire", FireActiveAbility::new,
            "tide", TideActiveAbility::new,
            "frost", FrostActiveAbility::new,
            "wind", WindActiveAbility::new,
            "earth", EarthActiveAbility::new,
            "thunder", ThunderActiveAbility::new
    );
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
        getServer().getPluginManager().registerEvents(new PlayerDamageListener(this), this);

        AdminCommand adminCommand = new AdminCommand(this);
        AspectsCommand aspectsCommand = new AspectsCommand(this, adminCommand);
        AspectsTabCompleter tabCompleter = new AspectsTabCompleter(this);
        PluginCommand command = getCommand("aspects");
        if (command != null) {
            command.setExecutor(aspectsCommand);
            command.setTabCompleter(tabCompleter);
        }

        for (Player player : getServer().getOnlinePlayers()) {
            aspectManager.loadOrAssign(player, () -> {});
        }

        getLogger().info("Aspects enabled with " + aspectRegistry.enabled().size() + " enabled aspect(s).");
    }

    @Override
    public void onDisable() {
        if (aspectManager != null) {
            for (Player player : getServer().getOnlinePlayers()) {
                aspectManager.unload(player);
            }
        }
        if (dataRepository != null) {
            dataRepository.close();
        }
    }

    private AspectRegistry buildRegistry(Map<String, AspectDefinition> definitions) {
        AspectRegistry registry = new AspectRegistry();
        for (AspectDefinition definition : definitions.values()) {
            Supplier<PassiveAbility> passiveFactory = PASSIVE_FACTORIES.get(definition.id());
            Supplier<Ability> activeFactory = ACTIVE_FACTORIES.get(definition.id());
            if (passiveFactory == null || activeFactory == null) {
                getLogger().info("Aspect '" + definition.id() +
                        "' is defined in aspects.yml but not yet implemented in code; skipping registration.");
                continue;
            }
            registry.register(new Aspect(definition, passiveFactory.get(), activeFactory.get()));
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
