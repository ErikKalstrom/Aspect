package com.aspects.smp.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code ConfigManager} backfills messages.yml keys missing from an on-disk file with the jar's
 * bundled copy, so a server upgrading from an older plugin version doesn't see a raw key name
 * instead of real text for a newly added message. This verifies the plain-map merge it relies on
 * instead of Bukkit's "defaults section" mechanism, which {@code getValues(true)} does NOT consult
 * (confirmed directly: an unset Bukkit defaults section is silently ignored by getValues).
 */
class ConfigManagerDefaultsTest {

    @Test
    void mergingBundledThenOnDisk_onDiskKeyWins_bundledOnlyKeyIsBackfilled() {
        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(new StringReader(
                "first-join:\n  line1: \"Custom line 1\"\n"));
        YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new StringReader(
                "first-join:\n  line1: \"Default line 1\"\n  line4: \"Default line 4\"\n"));

        Map<String, String> merged = new HashMap<>();
        for (Map.Entry<String, Object> entry : bundled.getValues(true).entrySet()) {
            if (entry.getValue() instanceof String str) {
                merged.put(entry.getKey(), str);
            }
        }
        for (Map.Entry<String, Object> entry : onDisk.getValues(true).entrySet()) {
            if (entry.getValue() instanceof String str) {
                merged.put(entry.getKey(), str);
            }
        }

        assertEquals("Custom line 1", merged.get("first-join.line1"));
        assertEquals("Default line 4", merged.get("first-join.line4"));
    }
}
