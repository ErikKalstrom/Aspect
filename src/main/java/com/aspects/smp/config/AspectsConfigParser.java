package com.aspects.smp.config;

import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.aspect.AspectRarity;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AspectsConfigParser {
    private AspectsConfigParser() {}

    public static GeneralConfig parseGeneral(FileConfiguration yml, List<String> errors) {
        boolean debug = yml.getBoolean("general.debug", false);
        String sqliteFile = yml.getString("storage.sqlite-file", "playerdata.db");
        boolean firstJoinEnabled = yml.getBoolean("first-join.enabled", true);
        String firstJoinSound = yml.getString("first-join.sound", "entity.player.levelup");
        String firstJoinParticle = yml.getString("first-join.particle", "TOTEM_OF_UNDYING");
        int firstJoinParticleCount = yml.getInt("first-join.particle-count", 20);
        List<String> blockedWorlds = yml.getStringList("world-restrictions.blocked-worlds");
        boolean excludeCurrentOnReroll = yml.getBoolean("reroll.exclude-current-aspect", true);
        boolean revealAnimationEnabled = yml.getBoolean("reroll.reveal-animation-enabled", true);

        if (sqliteFile == null || sqliteFile.isBlank()) {
            errors.add("storage.sqlite-file must not be blank");
            sqliteFile = "playerdata.db";
        }
        if (firstJoinParticleCount < 0) {
            errors.add("first-join.particle-count must not be negative");
        }

        return new GeneralConfig(debug, sqliteFile, firstJoinEnabled, firstJoinSound,
                firstJoinParticle, firstJoinParticleCount, blockedWorlds, excludeCurrentOnReroll,
                revealAnimationEnabled);
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

            Map<String, Object> settings = aspectSection.getValues(true);
            result.put(id, new AspectDefinition(id, displayName, description, rarity, weight, enabled, settings));
        }
        return result;
    }

    public static RecipesConfig parseRecipes(FileConfiguration yml, List<String> errors) {
        boolean enabled = yml.getBoolean("reroller.enabled", true);
        List<String> pattern = yml.getStringList("reroller.pattern");
        int resultAmount = yml.getInt("reroller.result-amount", 1);

        if (pattern.size() != 3) {
            errors.add("recipes.yml reroller.pattern must have exactly 3 rows");
            pattern = List.of("ADA", "DND", "ADA");
        } else {
            for (String row : pattern) {
                if (row.length() != 3) {
                    errors.add("recipes.yml reroller.pattern rows must each be exactly 3 characters");
                    break;
                }
            }
        }

        Map<Character, Material> ingredients = new LinkedHashMap<>();
        ConfigurationSection ingredientsSection = yml.getConfigurationSection("reroller.ingredients");
        if (ingredientsSection == null) {
            errors.add("recipes.yml is missing the 'reroller.ingredients' section");
        } else {
            for (String key : ingredientsSection.getKeys(false)) {
                if (key.length() != 1) {
                    errors.add("recipes.yml reroller.ingredients key '" + key + "' must be a single character");
                    continue;
                }
                if (key.equals(" ")) {
                    errors.add("recipes.yml reroller.ingredients must not use a space as a key (space means an empty pattern slot)");
                    continue;
                }
                String materialName = ingredientsSection.getString(key);
                Material material = materialName == null ? null : Material.matchMaterial(materialName);
                if (material == null) {
                    errors.add("recipes.yml reroller.ingredients." + key + " has unknown material '" + materialName + "'");
                    continue;
                }
                ingredients.put(key.charAt(0), material);
            }

            for (String row : pattern) {
                for (char c : row.toCharArray()) {
                    if (c != ' ' && !ingredients.containsKey(c)) {
                        errors.add("recipes.yml reroller.pattern uses character '" + c + "' with no matching ingredient");
                    }
                }
            }

            for (Character ingredientKey : ingredients.keySet()) {
                boolean used = pattern.stream().anyMatch(row -> row.indexOf(ingredientKey) >= 0);
                if (!used) {
                    errors.add("recipes.yml reroller.ingredients key '" + ingredientKey + "' is not used anywhere in the pattern");
                }
            }
        }

        if (resultAmount < 1) {
            errors.add("recipes.yml reroller.result-amount must be at least 1");
            resultAmount = 1;
        }

        return new RecipesConfig(enabled, pattern, ingredients, resultAmount);
    }
}
