package com.aspects.smp.config;

import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.aspect.AspectRarity;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AspectsConfigParser {
    /**
     * The config.yml structure version this plugin version understands. Bump this, and add a branch
     * keyed off the stored value, whenever a future change needs to migrate an older config.yml
     * rather than just reading it with a new default — there is nothing to migrate from yet, since
     * this is the first versioned config.
     */
    public static final int CURRENT_CONFIG_VERSION = 1;

    private AspectsConfigParser() {}

    public static GeneralConfig parseGeneral(FileConfiguration yml, List<String> errors) {
        int configVersion = yml.getInt("config-version", CURRENT_CONFIG_VERSION);
        if (configVersion > CURRENT_CONFIG_VERSION) {
            errors.add("config.yml config-version (" + configVersion + ") is newer than this plugin " +
                    "version supports (" + CURRENT_CONFIG_VERSION + ") -- was the plugin downgraded?");
        }
        boolean debug = yml.getBoolean("general.debug", false);
        String sqliteFile = yml.getString("storage.sqlite-file", "playerdata.db");
        boolean firstJoinEnabled = yml.getBoolean("first-join.enabled", true);
        String firstJoinSound = yml.getString("first-join.sound", "entity.player.levelup");
        String firstJoinParticle = yml.getString("first-join.particle", "TOTEM_OF_UNDYING");
        int firstJoinParticleCount = yml.getInt("first-join.particle-count", 20);
        List<String> blockedWorlds = yml.getStringList("world-restrictions.blocked-worlds");
        boolean excludeCurrentOnReroll = yml.getBoolean("reroll.exclude-current-aspect", true);
        boolean revealAnimationEnabled = yml.getBoolean("reroll.reveal-animation-enabled", true);
        boolean resetImmediateReassign = yml.getBoolean("reset.immediate-reassign", true);
        boolean respectWorldGuard = yml.getBoolean("world-restrictions.respect-worldguard", true);

        if (sqliteFile == null || sqliteFile.isBlank()) {
            errors.add("config.yml storage.sqlite-file must not be blank");
            sqliteFile = "playerdata.db";
        }
        if (firstJoinParticleCount < 0) {
            errors.add("config.yml first-join.particle-count must not be negative");
            firstJoinParticleCount = 20;
        }

        return new GeneralConfig(configVersion, debug, sqliteFile, firstJoinEnabled, firstJoinSound,
                firstJoinParticle, firstJoinParticleCount, blockedWorlds, excludeCurrentOnReroll,
                revealAnimationEnabled, resetImmediateReassign, respectWorldGuard);
    }

    public static Map<String, AspectDefinition> parseAspects(FileConfiguration yml, List<String> errors) {
        Map<String, AspectDefinition> result = new LinkedHashMap<>();
        ConfigurationSection section = yml.getConfigurationSection("aspects");
        if (section == null) {
            errors.add("aspects.yml is missing the top-level 'aspects' section");
            return result;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection aspectSection = section.getConfigurationSection(id);
            if (aspectSection == null) {
                continue;
            }

            boolean enabled = aspectSection.getBoolean("enabled", false);
            String displayName = aspectSection.getString("display-name", id);
            String description = aspectSection.getString("description", "");
            String rarityRaw = aspectSection.getString("rarity", "COMMON");
            double weight = aspectSection.getDouble("weight", 0);
            String iconRaw = aspectSection.getString("icon", "STONE");

            AspectRarity rarity;
            try {
                rarity = AspectRarity.valueOf(rarityRaw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.add("aspects." + id + ".rarity has unknown value '" + rarityRaw + "'");
                rarity = AspectRarity.COMMON;
            }
            if (weight < 0) {
                errors.add("aspects." + id + ".weight must not be negative");
                weight = 0;
            }
            Material icon = Material.matchMaterial(iconRaw);
            if (icon == null) {
                errors.add("aspects." + id + ".icon has unknown material '" + iconRaw + "'");
                icon = Material.STONE;
            } else if (icon.name().equals("AIR") || icon.name().endsWith("_AIR")) {
                errors.add("aspects." + id + ".icon must not be an air material (" + iconRaw + ") — icons must be displayable items");
                icon = Material.STONE;
            }

            Map<String, Object> settings = aspectSection.getValues(true);
            result.put(id, new AspectDefinition(id, displayName, description, rarity, weight, enabled, icon, settings));
        }
        return result;
    }

    public static RecipesConfig parseRecipes(FileConfiguration yml, List<String> errors) {
        boolean enabled = yml.getBoolean("reroller.enabled", true);
        int resultAmount = yml.getInt("reroller.result-amount", 1);

        List<String> pattern = yml.getStringList("reroller.pattern");
        Map<Character, Material> ingredients = new LinkedHashMap<>();
        List<String> recipeErrors = new ArrayList<>();

        boolean patternValid = pattern.size() == 3 && pattern.stream().allMatch(row -> row.length() == 3);
        if (pattern.size() != 3) {
            recipeErrors.add("recipes.yml reroller.pattern must have exactly 3 rows");
        } else if (!patternValid) {
            recipeErrors.add("recipes.yml reroller.pattern rows must each be exactly 3 characters");
        }

        ConfigurationSection ingredientsSection = yml.getConfigurationSection("reroller.ingredients");
        if (ingredientsSection == null) {
            recipeErrors.add("recipes.yml is missing the 'reroller.ingredients' section");
        } else {
            for (String key : ingredientsSection.getKeys(false)) {
                if (key.length() != 1) {
                    recipeErrors.add("recipes.yml reroller.ingredients key '" + key + "' must be a single character");
                    continue;
                }
                if (key.equals(" ")) {
                    recipeErrors.add("recipes.yml reroller.ingredients must not use a space as a key (space means an empty pattern slot)");
                    continue;
                }
                String materialName = ingredientsSection.getString(key);
                Material material = materialName == null ? null : Material.matchMaterial(materialName);
                if (material == null) {
                    recipeErrors.add("recipes.yml reroller.ingredients." + key + " has unknown material '" + materialName + "'");
                    continue;
                }
                ingredients.put(key.charAt(0), material);
            }
        }

        if (patternValid && ingredientsSection != null) {
            for (String row : pattern) {
                for (char c : row.toCharArray()) {
                    if (c != ' ' && !ingredients.containsKey(c)) {
                        recipeErrors.add("recipes.yml reroller.pattern uses character '" + c + "' with no matching ingredient");
                    }
                }
            }
            for (Character ingredientKey : ingredients.keySet()) {
                boolean used = pattern.stream().anyMatch(row -> row.indexOf(ingredientKey) >= 0);
                if (!used) {
                    recipeErrors.add("recipes.yml reroller.ingredients key '" + ingredientKey + "' is not used anywhere in the pattern");
                }
            }
        }

        // Any recipe-related problem falls back to the WHOLE shipped default recipe (pattern and
        // ingredients together), never a mix of valid and broken pieces -- a pattern fixed up on its
        // own but paired with the server's own (unrelated) custom ingredients, or vice versa, could
        // still leave unmatched characters and fail to register at all.
        if (!recipeErrors.isEmpty()) {
            errors.addAll(recipeErrors);
            pattern = List.of("ADA", "DND", "ADA");
            ingredients = defaultIngredients();
        }

        if (resultAmount < 1) {
            errors.add("recipes.yml reroller.result-amount must be at least 1");
            resultAmount = 1;
        }

        return new RecipesConfig(enabled, pattern, ingredients, resultAmount);
    }

    private static Map<Character, Material> defaultIngredients() {
        Map<Character, Material> defaults = new LinkedHashMap<>();
        defaults.put('A', Material.AMETHYST_SHARD);
        defaults.put('D', Material.DIAMOND);
        defaults.put('N', Material.NETHER_STAR);
        return defaults;
    }
}
