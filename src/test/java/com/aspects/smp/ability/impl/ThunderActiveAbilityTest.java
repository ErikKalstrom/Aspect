package com.aspects.smp.ability.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThunderActiveAbilityTest {

    private static final double DELTA = 1e-9;

    @Test
    void effectiveDistance_noHit_returnsMaxDistance() {
        assertEquals(7.0, ThunderActiveAbility.effectiveDistance(null, 7.0, 0.5), DELTA);
    }

    @Test
    void effectiveDistance_hitFarAway_clampsToMaxDistance() {
        assertEquals(7.0, ThunderActiveAbility.effectiveDistance(100.0, 7.0, 0.5), DELTA);
    }

    @Test
    void effectiveDistance_hitWithinRange_subtractsSafetyMargin() {
        assertEquals(2.5, ThunderActiveAbility.effectiveDistance(3.0, 7.0, 0.5), DELTA);
    }

    @Test
    void effectiveDistance_hitVeryClose_clampsToZeroNotNegative() {
        assertEquals(0.0, ThunderActiveAbility.effectiveDistance(0.2, 7.0, 0.5), DELTA);
    }

    @Test
    void effectiveDistance_hitExactlyAtMargin_returnsZero() {
        assertEquals(0.0, ThunderActiveAbility.effectiveDistance(0.5, 7.0, 0.5), DELTA);
    }
}
