package com.aspects.smp.config;

import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.aspect.AspectRarity;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins down two properties of the actual shipped {@code aspects.yml} that are easy to silently
 * regress by editing the file without re-deriving the numbers, since nothing else in the suite reads
 * the bundled resource itself (every other parser test uses an inline fixture): every Aspect has a
 * real passive/active name and description (not a placeholder), and the rarity weights still
 * approximate the product spec's intended distribution.
 */
class BundledAspectsYamlTest {

    private static Map<String, AspectDefinition> parseBundledAspects() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                BundledAspectsYamlTest.class.getClassLoader().getResourceAsStream("aspects.yml"),
                StandardCharsets.UTF_8));
        List<String> errors = new ArrayList<>();
        Map<String, AspectDefinition> aspects = AspectsConfigParser.parseAspects(yml, errors);
        assertTrue(errors.isEmpty(), "bundled aspects.yml must parse without validation errors: " + errors);
        return aspects;
    }

    @Test
    void bundledAspectsYaml_hasAllEightDefaultAspects() {
        Map<String, AspectDefinition> aspects = parseBundledAspects();
        assertEquals(
                java.util.Set.of("fire", "tide", "frost", "thunder", "void", "earth", "wind", "blood"),
                aspects.keySet());
    }

    @Test
    void bundledAspectsYaml_everyAspectHasRealPassiveAndActiveNamesAndDescriptions() {
        Map<String, AspectDefinition> aspects = parseBundledAspects();
        for (AspectDefinition definition : aspects.values()) {
            String id = definition.id();
            assertFalse(definition.passiveName().endsWith(" Passive"),
                    id + "'s passive name looks like the generic fallback: " + definition.passiveName());
            assertFalse(definition.activeName().endsWith(" Active"),
                    id + "'s active name looks like the generic fallback: " + definition.activeName());
            assertFalse(definition.passiveDescription().isBlank(), id + " is missing a passive description");
            assertFalse(definition.activeDescription().isBlank(), id + " is missing an active description");
        }
    }

    @Test
    void bundledAspectsYaml_rarityWeightsApproximateIntendedDistribution() {
        Map<String, AspectDefinition> aspects = parseBundledAspects();
        Map<AspectRarity, Double> totalByRarity = aspects.values().stream()
                .collect(Collectors.groupingBy(AspectDefinition::rarity, Collectors.summingDouble(AspectDefinition::weight)));
        double total = totalByRarity.values().stream().mapToDouble(Double::doubleValue).sum();

        // The product spec's suggested 40/25/20/10/5% Common/Uncommon/Rare/Epic/Legendary split,
        // renormalized across the four tiers actually used (none of the 8 default Aspects is
        // Uncommon) -- see README.md and aspects.yml's own header comment for the derivation.
        assertPercentWithinTolerance(53.3, totalByRarity.getOrDefault(AspectRarity.COMMON, 0.0) / total * 100);
        assertPercentWithinTolerance(26.7, totalByRarity.getOrDefault(AspectRarity.RARE, 0.0) / total * 100);
        assertPercentWithinTolerance(13.3, totalByRarity.getOrDefault(AspectRarity.EPIC, 0.0) / total * 100);
        assertPercentWithinTolerance(6.7, totalByRarity.getOrDefault(AspectRarity.LEGENDARY, 0.0) / total * 100);
        assertEquals(0.0, totalByRarity.getOrDefault(AspectRarity.UNCOMMON, 0.0),
                "no default Aspect is expected to be Uncommon");
    }

    private static void assertPercentWithinTolerance(double expected, double actual) {
        assertTrue(Math.abs(expected - actual) < 1.0,
                "expected ~" + expected + "%% but was " + actual + "%%");
    }
}
