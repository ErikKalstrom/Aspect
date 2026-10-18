package com.aspects.smp.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code ConfigManager}'s private {@code loadYaml}/{@code parse} cannot be unit-tested directly
 * (they need a real {@code JavaPlugin}, which — like every other live-Bukkit-dependent piece of this
 * codebase — is manual-verification-only). These tests instead pin down the two underlying Bukkit
 * mechanisms that behavior relies on, so a future Paper/Bukkit version change would be caught here
 * rather than silently changing ConfigManager's behavior.
 */
class ConfigManagerDefaultsTest {

    /**
     * {@code ConfigManager} backfills messages.yml keys missing from an on-disk file with the jar's
     * bundled copy, so a server upgrading from an older plugin version doesn't see a raw key name
     * instead of real text for a newly added message. This verifies the plain-map merge it relies on
     * instead of Bukkit's "defaults section" mechanism, which {@code getValues(true)} does NOT consult
     * (confirmed directly: an unset Bukkit defaults section is silently ignored by getValues).
     */
    @Test
    void puttingBundledThenOnDisk_onDiskKeyWins_bundledOnlyKeyIsBackfilled() {
        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(new StringReader(
                "first-join:\n  line1: \"Custom line 1\"\n"));
        YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new StringReader(
                "first-join:\n  line1: \"Default line 1\"\n  line4: \"Default line 4\"\n"));

        Map<String, String> merged = new HashMap<>();
        ConfigManager.putStringValues(merged, bundled);
        ConfigManager.putStringValues(merged, onDisk);

        assertEquals("Custom line 1", merged.get("first-join.line1"));
        assertEquals("Default line 4", merged.get("first-join.line4"));
    }

    /**
     * {@code ConfigManager.loadYaml} uses {@code new YamlConfiguration().load(file)} specifically
     * (instead of the static {@code YamlConfiguration.loadConfiguration(File)}, which swallows a
     * syntax error internally and returns an empty configuration with no way to detect it happened)
     * so a YAML syntax error can be reported as a validation error rather than silently applied as
     * "all defaults" while reporting success. This confirms the instance {@code load(File)} method
     * actually throws for malformed YAML, which that behavior depends on.
     */
    @Test
    void yamlConfigurationLoad_throwsOnSyntaxError() throws IOException {
        File file = File.createTempFile("aspects-test-malformed", ".yml");
        file.deleteOnExit();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("foo:\n  bar: [unclosed\n");
        }

        assertThrows(InvalidConfigurationException.class, () -> new YamlConfiguration().load(file));
    }
}
