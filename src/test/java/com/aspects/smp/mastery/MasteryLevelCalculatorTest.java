package com.aspects.smp.mastery;

import com.aspects.smp.config.MasteryLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasteryLevelCalculatorTest {

    private static final List<MasteryLevel> LEVELS = List.of(
            new MasteryLevel(25, "Novice"),
            new MasteryLevel(75, "Adept"),
            new MasteryLevel(150, "Expert"));

    @Test
    void currentLevel_belowFirstThreshold_isEmpty() {
        assertTrue(MasteryLevelCalculator.currentLevel(LEVELS, 10).isEmpty());
    }

    @Test
    void currentLevel_atExactThreshold_returnsThatLevel() {
        assertEquals("Novice", MasteryLevelCalculator.currentLevel(LEVELS, 25).orElseThrow().title());
    }

    @Test
    void currentLevel_betweenThresholds_returnsHighestReached() {
        assertEquals("Novice", MasteryLevelCalculator.currentLevel(LEVELS, 50).orElseThrow().title());
    }

    @Test
    void currentLevel_aboveAllThresholds_returnsHighest() {
        assertEquals("Expert", MasteryLevelCalculator.currentLevel(LEVELS, 500).orElseThrow().title());
    }

    @Test
    void currentLevel_doesNotAssumeSortedInput() {
        List<MasteryLevel> unsorted = List.of(
                new MasteryLevel(150, "Expert"),
                new MasteryLevel(25, "Novice"),
                new MasteryLevel(75, "Adept"));
        assertEquals("Adept", MasteryLevelCalculator.currentLevel(unsorted, 100).orElseThrow().title());
    }

    @Test
    void nextLevel_belowFirstThreshold_returnsFirst() {
        assertEquals("Novice", MasteryLevelCalculator.nextLevel(LEVELS, 10).orElseThrow().title());
    }

    @Test
    void nextLevel_atExactThreshold_returnsNextOneUp() {
        assertEquals("Adept", MasteryLevelCalculator.nextLevel(LEVELS, 25).orElseThrow().title());
    }

    @Test
    void nextLevel_aboveAllThresholds_isEmpty() {
        Optional<MasteryLevel> result = MasteryLevelCalculator.nextLevel(LEVELS, 500);
        assertTrue(result.isEmpty());
    }

    @Test
    void currentLevel_emptyLevelsList_isAlwaysEmpty() {
        assertTrue(MasteryLevelCalculator.currentLevel(List.of(), 1000).isEmpty());
    }
}
