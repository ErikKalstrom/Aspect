package com.aspects.smp.ability.impl;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WindActiveAbilityTest {

    private static final double DELTA = 1e-9;

    @Test
    void horizontalFromYaw_zeroYaw_pointsTowardPositiveZ() {
        Vector result = WindActiveAbility.horizontalFromYaw(0f);
        assertEquals(0.0, result.getX(), DELTA);
        assertEquals(0.0, result.getY(), DELTA);
        assertEquals(1.0, result.getZ(), DELTA);
    }

    @Test
    void horizontalFromYaw_ninetyDegrees_pointsTowardNegativeX() {
        Vector result = WindActiveAbility.horizontalFromYaw(90f);
        assertEquals(-1.0, result.getX(), DELTA);
        assertEquals(0.0, result.getY(), DELTA);
        assertEquals(0.0, result.getZ(), DELTA);
    }

    @Test
    void horizontalFromYaw_negativeNinetyDegrees_pointsTowardPositiveX() {
        Vector result = WindActiveAbility.horizontalFromYaw(-90f);
        assertEquals(1.0, result.getX(), DELTA);
        assertEquals(0.0, result.getY(), DELTA);
        assertEquals(0.0, result.getZ(), DELTA);
    }

    @Test
    void horizontalFromYaw_oneEightyDegrees_pointsTowardNegativeZ() {
        Vector result = WindActiveAbility.horizontalFromYaw(180f);
        assertEquals(0.0, result.getX(), DELTA);
        assertEquals(0.0, result.getY(), DELTA);
        assertEquals(-1.0, result.getZ(), DELTA);
    }

    @Test
    void horizontalFromYaw_alwaysUnitLength() {
        for (float yaw = -360f; yaw <= 360f; yaw += 17f) {
            Vector result = WindActiveAbility.horizontalFromYaw(yaw);
            assertEquals(1.0, result.length(), DELTA, "yaw=" + yaw);
        }
    }
}
