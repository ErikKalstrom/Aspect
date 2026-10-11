package com.aspects.smp.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

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
}
