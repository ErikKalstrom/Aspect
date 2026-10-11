package com.aspects.smp.config;

import com.aspects.smp.aspect.AspectDefinition;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AspectsConfigParserTest {

    @Test
    void parseGeneral_acceptsValidValues() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "storage:\n  sqlite-file: data.db\nfirst-join:\n  particle-count: 10\n"));
        List<String> errors = new ArrayList<>();

        GeneralConfig config = AspectsConfigParser.parseGeneral(yml, errors);

        assertTrue(errors.isEmpty());
        assertEquals("data.db", config.sqliteFile());
        assertEquals(10, config.firstJoinParticleCount());
    }

    @Test
    void parseGeneral_rejectsNegativeParticleCount() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "first-join:\n  particle-count: -5\n"));
        List<String> errors = new ArrayList<>();

        AspectsConfigParser.parseGeneral(yml, errors);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("particle-count"));
    }

    @Test
    void parseGeneral_rejectsBlankSqliteFile() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "storage:\n  sqlite-file: \"\"\n"));
        List<String> errors = new ArrayList<>();

        GeneralConfig config = AspectsConfigParser.parseGeneral(yml, errors);

        assertEquals(1, errors.size());
        assertEquals("playerdata.db", config.sqliteFile());
    }

    @Test
    void parseAspects_rejectsUnknownRarityButStillReturnsDefinition() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "aspects:\n  fire:\n    enabled: true\n    rarity: NOT_A_RARITY\n    weight: 10\n"));
        List<String> errors = new ArrayList<>();

        Map<String, AspectDefinition> definitions = AspectsConfigParser.parseAspects(yml, errors);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("rarity"));
        assertTrue(definitions.containsKey("fire"));
    }

    @Test
    void parseAspects_rejectsNegativeWeight() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "aspects:\n  fire:\n    enabled: true\n    rarity: RARE\n    weight: -3\n"));
        List<String> errors = new ArrayList<>();

        AspectsConfigParser.parseAspects(yml, errors);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("weight"));
    }

    @Test
    void parseAspects_flattensNestedSettingsWithDottedKeys() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "aspects:\n  fire:\n    enabled: true\n    rarity: RARE\n    weight: 10\n" +
                        "    active:\n      cooldown-seconds: 25\n      radius: 4.0\n"));
        List<String> errors = new ArrayList<>();

        Map<String, AspectDefinition> definitions = AspectsConfigParser.parseAspects(yml, errors);

        assertTrue(errors.isEmpty());
        AspectDefinition fire = definitions.get("fire");
        assertEquals(25.0, fire.getDouble("active.cooldown-seconds", -1));
        assertEquals(4.0, fire.getDouble("active.radius", -1));
    }

    @Test
    void parseAspects_missingSectionProducesError() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader("other-key: true\n"));
        List<String> errors = new ArrayList<>();

        AspectsConfigParser.parseAspects(yml, errors);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("aspects"));
    }

    @Test
    void parseRecipes_acceptsValidConfig() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "reroller:\n" +
                "  enabled: true\n" +
                "  pattern:\n" +
                "    - \"ADA\"\n" +
                "    - \"DND\"\n" +
                "    - \"ADA\"\n" +
                "  ingredients:\n" +
                "    A: AMETHYST_SHARD\n" +
                "    D: DIAMOND\n" +
                "    N: NETHER_STAR\n" +
                "  result-amount: 1\n"));
        List<String> errors = new ArrayList<>();

        RecipesConfig config = AspectsConfigParser.parseRecipes(yml, errors);

        assertTrue(errors.isEmpty());
        assertTrue(config.rerollerEnabled());
        assertEquals(List.of("ADA", "DND", "ADA"), config.rerollerPattern());
        assertEquals(org.bukkit.Material.AMETHYST_SHARD, config.rerollerIngredients().get('A'));
        assertEquals(org.bukkit.Material.DIAMOND, config.rerollerIngredients().get('D'));
        assertEquals(org.bukkit.Material.NETHER_STAR, config.rerollerIngredients().get('N'));
        assertEquals(1, config.rerollerResultAmount());
    }

    @Test
    void parseRecipes_rejectsPatternWithWrongRowCount() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "reroller:\n" +
                "  pattern:\n" +
                "    - \"ADA\"\n" +
                "    - \"DND\"\n" +
                "  ingredients:\n" +
                "    A: AMETHYST_SHARD\n" +
                "    D: DIAMOND\n"));
        List<String> errors = new ArrayList<>();

        AspectsConfigParser.parseRecipes(yml, errors);

        assertTrue(errors.stream().anyMatch(e -> e.contains("exactly 3 rows")));
    }

    @Test
    void parseRecipes_rejectsRowWithWrongLength() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "reroller:\n" +
                "  pattern:\n" +
                "    - \"AD\"\n" +
                "    - \"DND\"\n" +
                "    - \"ADA\"\n" +
                "  ingredients:\n" +
                "    A: AMETHYST_SHARD\n" +
                "    D: DIAMOND\n" +
                "    N: NETHER_STAR\n"));
        List<String> errors = new ArrayList<>();

        AspectsConfigParser.parseRecipes(yml, errors);

        assertTrue(errors.stream().anyMatch(e -> e.contains("exactly 3 characters")));
    }

    @Test
    void parseRecipes_rejectsUnknownMaterial() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "reroller:\n" +
                "  pattern:\n" +
                "    - \"ADA\"\n" +
                "    - \"DND\"\n" +
                "    - \"ADA\"\n" +
                "  ingredients:\n" +
                "    A: NOT_A_REAL_MATERIAL\n" +
                "    D: DIAMOND\n" +
                "    N: NETHER_STAR\n"));
        List<String> errors = new ArrayList<>();

        AspectsConfigParser.parseRecipes(yml, errors);

        assertTrue(errors.stream().anyMatch(e -> e.contains("unknown material")));
    }

    @Test
    void parseRecipes_rejectsPatternCharacterWithNoIngredient() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "reroller:\n" +
                "  pattern:\n" +
                "    - \"ADA\"\n" +
                "    - \"DXD\"\n" +
                "    - \"ADA\"\n" +
                "  ingredients:\n" +
                "    A: AMETHYST_SHARD\n" +
                "    D: DIAMOND\n"));
        List<String> errors = new ArrayList<>();

        AspectsConfigParser.parseRecipes(yml, errors);

        assertTrue(errors.stream().anyMatch(e -> e.contains("no matching ingredient")));
    }

    @Test
    void parseRecipes_allowsSpaceAsEmptySlot() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "reroller:\n" +
                "  pattern:\n" +
                "    - \"A A\"\n" +
                "    - \"DND\"\n" +
                "    - \"A A\"\n" +
                "  ingredients:\n" +
                "    A: AMETHYST_SHARD\n" +
                "    D: DIAMOND\n" +
                "    N: NETHER_STAR\n"));
        List<String> errors = new ArrayList<>();

        AspectsConfigParser.parseRecipes(yml, errors);

        assertTrue(errors.isEmpty());
    }

    @Test
    void parseRecipes_rejectsResultAmountBelowOne() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "reroller:\n" +
                "  pattern:\n" +
                "    - \"ADA\"\n" +
                "    - \"DND\"\n" +
                "    - \"ADA\"\n" +
                "  ingredients:\n" +
                "    A: AMETHYST_SHARD\n" +
                "    D: DIAMOND\n" +
                "    N: NETHER_STAR\n" +
                "  result-amount: 0\n"));
        List<String> errors = new ArrayList<>();

        RecipesConfig config = AspectsConfigParser.parseRecipes(yml, errors);

        assertTrue(errors.stream().anyMatch(e -> e.contains("result-amount")));
        assertEquals(1, config.rerollerResultAmount());
    }

    @Test
    void parseRecipes_rejectsSpaceAsIngredientKey() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "reroller:\n" +
                "  pattern:\n" +
                "    - \"ADA\"\n" +
                "    - \"DND\"\n" +
                "    - \"ADA\"\n" +
                "  ingredients:\n" +
                "    A: AMETHYST_SHARD\n" +
                "    D: DIAMOND\n" +
                "    N: NETHER_STAR\n" +
                "    \" \": DIAMOND\n"));
        List<String> errors = new ArrayList<>();

        AspectsConfigParser.parseRecipes(yml, errors);

        assertTrue(errors.stream().anyMatch(e -> e.contains("must not use a space")));
    }

    @Test
    void parseRecipes_rejectsIngredientKeyNotUsedInPattern() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "reroller:\n" +
                "  pattern:\n" +
                "    - \"ADA\"\n" +
                "    - \"DDD\"\n" +
                "    - \"ADA\"\n" +
                "  ingredients:\n" +
                "    A: AMETHYST_SHARD\n" +
                "    D: DIAMOND\n" +
                "    N: NETHER_STAR\n"));
        List<String> errors = new ArrayList<>();

        AspectsConfigParser.parseRecipes(yml, errors);

        assertTrue(errors.stream().anyMatch(e -> e.contains("is not used anywhere in the pattern")));
    }

    @Test
    void parseAspects_parsesValidIcon() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "aspects:\n" +
                "  fire:\n" +
                "    enabled: true\n" +
                "    icon: BLAZE_POWDER\n"));
        List<String> errors = new ArrayList<>();

        Map<String, AspectDefinition> aspects = AspectsConfigParser.parseAspects(yml, errors);

        assertTrue(errors.isEmpty());
        assertEquals(org.bukkit.Material.BLAZE_POWDER, aspects.get("fire").icon());
    }

    @Test
    void parseAspects_rejectsUnknownIcon() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "aspects:\n" +
                "  fire:\n" +
                "    enabled: true\n" +
                "    icon: NOT_A_REAL_MATERIAL\n"));
        List<String> errors = new ArrayList<>();

        AspectsConfigParser.parseAspects(yml, errors);

        assertTrue(errors.stream().anyMatch(e -> e.contains("icon") && e.contains("fire")));
    }

    @Test
    void parseAspects_defaultsIconToStoneWhenMissing() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "aspects:\n" +
                "  fire:\n" +
                "    enabled: true\n"));
        List<String> errors = new ArrayList<>();

        Map<String, AspectDefinition> aspects = AspectsConfigParser.parseAspects(yml, errors);

        assertTrue(errors.isEmpty());
        assertEquals(org.bukkit.Material.STONE, aspects.get("fire").icon());
    }

    @Test
    void parseAspects_rejectsAirAsIcon() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "aspects:\n" +
                "  fire:\n" +
                "    enabled: true\n" +
                "    icon: AIR\n"));
        List<String> errors = new ArrayList<>();

        Map<String, AspectDefinition> aspects = AspectsConfigParser.parseAspects(yml, errors);

        assertTrue(errors.stream().anyMatch(e -> e.contains("must not be an air material")));
        assertEquals(org.bukkit.Material.STONE, aspects.get("fire").icon());
    }

    @Test
    void parseGeneral_defaultsResetImmediateReassignToTrue() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(""));
        List<String> errors = new ArrayList<>();

        GeneralConfig config = AspectsConfigParser.parseGeneral(yml, errors);

        assertTrue(config.resetImmediateReassign());
    }

    @Test
    void parseGeneral_readsResetImmediateReassignFalse() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(
                "reset:\n  immediate-reassign: false\n"));
        List<String> errors = new ArrayList<>();

        GeneralConfig config = AspectsConfigParser.parseGeneral(yml, errors);

        assertFalse(config.resetImmediateReassign());
    }
}
