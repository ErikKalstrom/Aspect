package com.aspects.smp.config;

/** One mastery milestone: reached once a player has successfully activated an Aspect's active
 * ability at least {@code activations} times (with that Aspect currently or previously assigned). */
public record MasteryLevel(int activations, String title) {
}
