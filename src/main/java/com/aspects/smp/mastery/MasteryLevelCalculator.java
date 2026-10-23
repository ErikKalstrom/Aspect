package com.aspects.smp.mastery;

import com.aspects.smp.config.MasteryLevel;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Pure level-lookup logic, independent of Bukkit, so it can be unit-tested directly. */
public final class MasteryLevelCalculator {
    private MasteryLevelCalculator() {}

    /** The highest level whose activation threshold has been reached, if any. Does not assume
     * {@code levels} is sorted. */
    public static Optional<MasteryLevel> currentLevel(List<MasteryLevel> levels, int activations) {
        return levels.stream()
                .filter(level -> activations >= level.activations())
                .max(Comparator.comparingInt(MasteryLevel::activations));
    }

    /** The next level not yet reached, if any -- useful for showing "X more uses until Adept". */
    public static Optional<MasteryLevel> nextLevel(List<MasteryLevel> levels, int activations) {
        return levels.stream()
                .filter(level -> activations < level.activations())
                .min(Comparator.comparingInt(MasteryLevel::activations));
    }
}
