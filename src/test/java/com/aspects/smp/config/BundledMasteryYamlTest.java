package com.aspects.smp.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledMasteryYamlTest {

    @Test
    void bundledMasteryYaml_parsesCleanly_disabledByDefault_withOrderedLevels() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                BundledMasteryYamlTest.class.getClassLoader().getResourceAsStream("mastery.yml"),
                StandardCharsets.UTF_8));
        List<String> errors = new ArrayList<>();

        MasteryConfig config = AspectsConfigParser.parseMastery(yml, errors);

        assertTrue(errors.isEmpty(), "bundled mastery.yml must parse without validation errors: " + errors);
        assertFalse(config.enabled(), "mastery is off by default -- no live player data behind its numbers yet");
        assertEquals(5, config.levels().size());
        for (int i = 1; i < config.levels().size(); i++) {
            assertTrue(config.levels().get(i).activations() > config.levels().get(i - 1).activations(),
                    "levels must be strictly increasing by activation count");
        }
    }
}
