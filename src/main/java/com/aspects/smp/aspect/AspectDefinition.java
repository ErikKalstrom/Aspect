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

    public String getString(String key, String defaultValue) {
        Object value = settings.get(key);
        return (value instanceof String str) ? str : defaultValue;
    }

    public String passiveName() {
        return getString("passive.name", capitalizedId() + " Passive");
    }

    public String passiveDescription() {
        return getString("passive.description", "");
    }

    public String activeName() {
        return getString("active.name", capitalizedId() + " Active");
    }

    public String activeDescription() {
        return getString("active.description", "");
    }

    private String capitalizedId() {
        return id.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + id.substring(1);
    }
}
