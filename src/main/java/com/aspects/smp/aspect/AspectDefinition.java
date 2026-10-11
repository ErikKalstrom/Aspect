package com.aspects.smp.aspect;

import org.bukkit.Material;

import java.util.Map;

public record AspectDefinition(
        String id,
        String displayName,
        String description,
        AspectRarity rarity,
        double weight,
        boolean enabled,
        Material icon,
        Map<String, Object> settings
) {

    public double getDouble(String key, double defaultValue) {
        Object value = settings.get(key);
        return (value instanceof Number number) ? number.doubleValue() : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        Object value = settings.get(key);
        return (value instanceof Number number) ? number.intValue() : defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = settings.get(key);
        return (value instanceof Boolean bool) ? bool : defaultValue;
    }
}
