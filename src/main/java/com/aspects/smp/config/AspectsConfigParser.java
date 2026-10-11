package com.aspects.smp.config;

import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.aspect.AspectRarity;
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
        String firstJoinParticle = yml.getString("first-join.particle", "TOTEM");
        int firstJoinParticleCount = yml.getInt("first-join.particle-count", 20);
        List<String> blockedWorlds = yml.getStringList("world-restrictions.blocked-worlds");
        boolean excludeCurrentOnReroll = yml.getBoolean("reroll.exclude-current-aspect", true);

        if (sqliteFile == null || sqliteFile.isBlank()) {
            errors.add("storage.sqlite-file must not be blank");
            sqliteFile = "playerdata.db";
        }
        if (firstJoinParticleCount < 0) {
            errors.add("first-join.particle-count must not be negative");
        }

        return new GeneralConfig(debug, sqliteFile, firstJoinEnabled, firstJoinSound,
                firstJoinParticle, firstJoinParticleCount, blockedWorlds, excludeCurrentOnReroll);
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
}
