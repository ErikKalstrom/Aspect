package com.aspects.smp.config;

import com.aspects.smp.aspect.AspectDefinition;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConfigManager {
    private final JavaPlugin plugin;

    private GeneralConfig generalConfig;
    private Map<String, AspectDefinition> aspectDefinitions = Map.of();
    private Map<String, String> messages = Map.of();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() throws ConfigValidationException {
        plugin.saveDefaultConfig();
        saveResourceIfMissing("aspects.yml");
        saveResourceIfMissing("messages.yml");

        FileConfiguration configYml = loadYaml("config.yml");
        FileConfiguration aspectsYml = loadYaml("aspects.yml");
        FileConfiguration messagesYml = loadYaml("messages.yml");

        List<String> errors = new ArrayList<>();
        GeneralConfig stagedGeneral = AspectsConfigParser.parseGeneral(configYml, errors);
        Map<String, AspectDefinition> stagedAspects = AspectsConfigParser.parseAspects(aspectsYml, errors);

        Map<String, String> stagedMessages = new HashMap<>();
        for (Map.Entry<String, Object> entry : messagesYml.getValues(true).entrySet()) {
            if (entry.getValue() instanceof String str) {
                stagedMessages.put(entry.getKey(), str);
            }
        }

        if (!errors.isEmpty()) {
            throw new ConfigValidationException(errors);
        }

        this.generalConfig = stagedGeneral;
        this.aspectDefinitions = stagedAspects;
        this.messages = stagedMessages;
    }

    private void saveResourceIfMissing(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
    }

    private FileConfiguration loadYaml(String name) {
        File file = new File(plugin.getDataFolder(), name);
        return YamlConfiguration.loadConfiguration(file);
    }

    public GeneralConfig general() {
        return generalConfig;
    }

    public Map<String, AspectDefinition> aspectDefinitions() {
        return aspectDefinitions;
    }

    public String message(String key) {
        return messages.getOrDefault(key, key);
    }
}
