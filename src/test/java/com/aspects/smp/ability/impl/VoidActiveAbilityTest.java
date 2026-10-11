package com.aspects.smp.ability.impl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VoidActiveAbilityTest {

    @Test
    void pickSafeDistance_returnsMaxDistance_whenFarthestIsSafe() {
        assertEquals(7, VoidActiveAbility.pickSafeDistance(7, distance -> distance == 7));
    }

    @Test
    void pickSafeDistance_scansFromFarToNear_returnsFirstSafeOne() {
        assertEquals(4, VoidActiveAbility.pickSafeDistance(7, distance -> distance <= 4));
    }

    @Test
    void pickSafeDistance_returnsZero_whenNothingIsSafe() {
        assertEquals(0, VoidActiveAbility.pickSafeDistance(7, distance -> false));
    }

    @Test
    void pickSafeDistance_checksExactlyOneThroughMaxDistance_noOffByOne() {
        List<Integer> checked = new ArrayList<>();
        VoidActiveAbility.pickSafeDistance(3, distance -> {
            checked.add(distance);
            return false;
        });
        assertEquals(List.of(3, 2, 1), checked);
    }

    @Test
    void pickSafeDistance_returnsOne_whenOnlyClosestIsSafe() {
        assertEquals(1, VoidActiveAbility.pickSafeDistance(5, distance -> distance == 1));
    }
}
