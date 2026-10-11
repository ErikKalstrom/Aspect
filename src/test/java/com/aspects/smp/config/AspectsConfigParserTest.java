package com.aspects.smp.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

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
}
