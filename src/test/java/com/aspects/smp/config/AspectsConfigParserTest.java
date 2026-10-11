package com.aspects.smp.config;

import com.aspects.smp.aspect.AspectDefinition;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
