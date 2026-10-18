package com.aspects.smp.ability.impl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void scanForLandingY_sameHeightSafeFloor_returnsTop() {
        OptionalInt result = VoidActiveAbility.scanForLandingY(100, 97,
                y -> true, y -> y == 100, y -> true);
        assertEquals(100, result.getAsInt());
    }

    @Test
    void scanForLandingY_noHeadroomAboveTop_returnsEmpty() {
        OptionalInt result = VoidActiveAbility.scanForLandingY(100, 97,
                y -> y != 101, y -> true, y -> true);
        assertTrue(result.isEmpty());
    }

    @Test
    void scanForLandingY_stepsDownToFirstSafeFloor() {
        // No safe floor at 100 or 99; one block down at 98 the floor is finally safe.
        OptionalInt result = VoidActiveAbility.scanForLandingY(100, 97,
                y -> true, y -> y == 98, y -> true);
        assertEquals(98, result.getAsInt());
    }

    @Test
    void scanForLandingY_obstructionPartwayDown_stopsDescentAndReturnsEmpty() {
        // A non-passable layer at 99 must stop the scan -- it must never "skip past" it to the safe
        // floor at 96, even though 96 would otherwise be a valid candidate within range.
        Set<Integer> nonPassable = Set.of(99);
        List<Integer> checkedFloors = new ArrayList<>();
        OptionalInt result = VoidActiveAbility.scanForLandingY(100, 95,
                y -> !nonPassable.contains(y),
                y -> {
                    checkedFloors.add(y);
                    return y == 96;
                },
                y -> true);
        assertTrue(result.isEmpty(), "an obstruction above a safe floor must block reaching it");
        assertTrue(checkedFloors.stream().noneMatch(y -> y <= 99),
                "the scan must never evaluate a floor at or below the obstruction: checked " + checkedFloors);
    }

    @Test
    void scanForLandingY_safeFloorButProtectionDenied_returnsEmpty_doesNotContinueDeeper() {
        // Real solid ground exists at 99, but it's protection-denied. The player can't physically sink
        // through solid ground to try the otherwise-safe floor at 96 instead.
        List<Integer> checkedFloors = new ArrayList<>();
        OptionalInt result = VoidActiveAbility.scanForLandingY(100, 95,
                y -> true,
                y -> {
                    checkedFloors.add(y);
                    return y == 99 || y == 96;
                },
                y -> y != 99);
        assertTrue(result.isEmpty());
        assertTrue(checkedFloors.stream().noneMatch(y -> y < 99),
                "must not keep scanning past a denied-but-physically-safe floor: checked " + checkedFloors);
    }

    @Test
    void scanForLandingY_noSafeFloorWithinRange_returnsEmpty() {
        OptionalInt result = VoidActiveAbility.scanForLandingY(100, 97,
                y -> true, y -> false, y -> true);
        assertTrue(result.isEmpty());
    }

    @Test
    void scanForLandingY_respectsBottomBound_neverScansBelowIt() {
        List<Integer> checkedFloors = new ArrayList<>();
        VoidActiveAbility.scanForLandingY(100, 98,
                y -> true,
                y -> {
                    checkedFloors.add(y);
                    return false;
                },
                y -> true);
        assertTrue(checkedFloors.stream().allMatch(y -> y >= 98),
                "must never check below the given bottom: checked " + checkedFloors);
    }
}
