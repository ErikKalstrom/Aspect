package com.aspects.smp.aspect;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AspectDefinitionTest {

    private AspectDefinition definition(Map<String, Object> settings) {
        return new AspectDefinition("storm", "Storm", "A test Aspect.", AspectRarity.RARE, 10, true,
                Material.FEATHER, settings);
    }

    @Test
    void passiveName_usesConfiguredValue_whenPresent() {
        AspectDefinition definition = definition(Map.of("passive.name", "Tempest"));
        assertEquals("Tempest", definition.passiveName());
    }

    @Test
    void passiveName_fallsBackToCapitalizedIdPlusPassive_whenMissing() {
        AspectDefinition definition = definition(Map.of());
        assertEquals("Storm Passive", definition.passiveName());
    }

    @Test
    void activeName_usesConfiguredValue_whenPresent() {
        AspectDefinition definition = definition(Map.of("active.name", "Gale Force"));
        assertEquals("Gale Force", definition.activeName());
    }

    @Test
    void activeName_fallsBackToCapitalizedIdPlusActive_whenMissing() {
        AspectDefinition definition = definition(Map.of());
        assertEquals("Storm Active", definition.activeName());
    }

    @Test
    void passiveDescription_and_activeDescription_fallBackToEmpty_whenMissing() {
        AspectDefinition definition = definition(Map.of());
        assertEquals("", definition.passiveDescription());
        assertEquals("", definition.activeDescription());
    }
}
