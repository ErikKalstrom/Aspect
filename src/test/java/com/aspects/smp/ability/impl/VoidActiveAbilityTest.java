package com.aspects.smp.ability.impl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VoidActiveAbilityTest {

    @Test
    void pickSafeDistance_returnsMaxDistance_whenFarthestIsSafeAndPathIsClear() {
        assertEquals(7, VoidActiveAbility.pickSafeDistance(7, distance -> true, distance -> distance == 7));
    }

    @Test
    void pickSafeDistance_returnsFarthestValidLanding_whenCloserOnesAreAlsoValid() {
        assertEquals(4, VoidActiveAbility.pickSafeDistance(7, distance -> true, distance -> distance <= 4));
    }

    @Test
    void pickSafeDistance_returnsZero_whenNothingIsSafe() {
        assertEquals(0, VoidActiveAbility.pickSafeDistance(7, distance -> true, distance -> false));
    }

    @Test
    void pickSafeDistance_scansNearToFar_noOffByOne() {
        List<Integer> checked = new ArrayList<>();
        VoidActiveAbility.pickSafeDistance(3, distance -> true, distance -> {
            checked.add(distance);
            return false;
        });
        assertEquals(List.of(1, 2, 3), checked);
    }

    @Test
    void pickSafeDistance_returnsOne_whenOnlyClosestIsSafe() {
        assertEquals(1, VoidActiveAbility.pickSafeDistance(5, distance -> true, distance -> distance == 1));
    }

    @Test
    void pickSafeDistance_stopsAtWall_neverConsidersDistancesBeyondIt() {
        // A wall at distance 3 blocks the path. Distance 2 (before the wall) and distance 5 (beyond it) are
        // both otherwise valid landings, but 5 must never be reachable or even evaluated.
        List<Integer> landingChecks = new ArrayList<>();
        int result = VoidActiveAbility.pickSafeDistance(7,
                distance -> distance < 3,
                distance -> {
                    landingChecks.add(distance);
                    return distance == 2 || distance == 5;
                });
        assertEquals(2, result, "should land just short of the wall, not clip through it");
        assertEquals(List.of(1, 2), landingChecks, "distances at or beyond the wall must never be evaluated as landings");
    }

    @Test
    void pickSafeDistance_wallImmediatelyAdjacent_returnsZero() {
        assertEquals(0, VoidActiveAbility.pickSafeDistance(7, distance -> distance < 1, distance -> true));
    }
}
