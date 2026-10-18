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
    private RecipesConfig recipesConfig;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Parses and applies configuration unconditionally. Every individual malformed value already
     * falls back to a safe default inside {@link AspectsConfigParser} — this commits those
     * defaults and returns the validation warnings rather than failing the whole plugin over one
     * bad value. Used for the initial load in {@code onEnable()}, where there is no previous
     * known-good configuration to fall back to instead.
     */
    public List<String> loadAll() {
        StagedConfig staged = parse();
        apply(staged);
        return staged.errors();
    }

    /**
     * Parses a fresh configuration snapshot and only applies it if it is free of validation
     * errors, leaving the current configuration completely untouched otherwise. Used by
     * {@code /aspects admin reload}, which must never partially apply a broken configuration.
     */
    public List<String> tryReload() {
        StagedConfig staged = parse();
        if (staged.errors().isEmpty()) {
            apply(staged);
        }
        return staged.errors();
    }

    private StagedConfig parse() {
        plugin.saveDefaultConfig();
        saveResourceIfMissing("aspects.yml");
        saveResourceIfMissing("messages.yml");
        saveResourceIfMissing("recipes.yml");

        FileConfiguration configYml = loadYaml("config.yml");
        FileConfiguration aspectsYml = loadYaml("aspects.yml");
        FileConfiguration messagesYml = loadYaml("messages.yml");
        FileConfiguration recipesYml = loadYaml("recipes.yml");

        List<String> errors = new ArrayList<>();
        GeneralConfig stagedGeneral = AspectsConfigParser.parseGeneral(configYml, errors);
        Map<String, AspectDefinition> stagedAspects = AspectsConfigParser.parseAspects(aspectsYml, errors);
        RecipesConfig stagedRecipes = AspectsConfigParser.parseRecipes(recipesYml, errors);

        Map<String, String> stagedMessages = new HashMap<>();
        for (Map.Entry<String, Object> entry : messagesYml.getValues(true).entrySet()) {
            if (entry.getValue() instanceof String str) {
                stagedMessages.put(entry.getKey(), str);
            }
        }

        return new StagedConfig(stagedGeneral, stagedAspects, stagedMessages, stagedRecipes, errors);
    }

    private void apply(StagedConfig staged) {
        this.generalConfig = staged.general();
        this.aspectDefinitions = staged.aspects();
        this.messages = staged.messages();
        this.recipesConfig = staged.recipes();
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

    public RecipesConfig recipes() {
        return recipesConfig;
    }

    private record StagedConfig(GeneralConfig general, Map<String, AspectDefinition> aspects,
                                 Map<String, String> messages, RecipesConfig recipes, List<String> errors) {
    }
}
