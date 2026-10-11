package com.aspects.smp.aspect;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.PassiveAbility;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AspectAssignmentServiceTest {

    private static final PassiveAbility NO_OP_PASSIVE = new PassiveAbility() {
        @Override
        public void onEquip(Player player, AspectDefinition definition) {
        }

        @Override
        public void onUnequip(Player player) {
        }
    };
    private static final Ability NO_OP_ACTIVE = context -> true;

    private Aspect aspect(String id, boolean enabled, double weight) {
        return new Aspect(
                new AspectDefinition(id, id, "", AspectRarity.COMMON, weight, enabled, Map.of()),
                NO_OP_PASSIVE, NO_OP_ACTIVE);
    }

    @Test
    void selectRandom_excludesDisabledAspects() {
        List<Aspect> pool = List.of(aspect("fire", false, 10), aspect("tide", true, 10));
        AspectAssignmentService service = new AspectAssignmentService(() -> 0.0);

        Optional<Aspect> picked = service.selectRandom(pool, null);

        assertTrue(picked.isPresent());
        assertEquals("tide", picked.get().id());
    }

    @Test
    void selectRandom_excludesZeroWeightAspects() {
        List<Aspect> pool = List.of(aspect("fire", true, 0), aspect("tide", true, 10));
        AspectAssignmentService service = new AspectAssignmentService(() -> 0.0);

        Optional<Aspect> picked = service.selectRandom(pool, null);

        assertTrue(picked.isPresent());
        assertEquals("tide", picked.get().id());
    }

    @Test
    void selectRandom_excludesCurrentAspectWhenAnotherIsEligible() {
        List<Aspect> pool = List.of(aspect("fire", true, 10), aspect("tide", true, 10));
        AspectAssignmentService service = new AspectAssignmentService(() -> 0.0);

        Optional<Aspect> picked = service.selectRandom(pool, "fire");

        assertTrue(picked.isPresent());
        assertEquals("tide", picked.get().id());
    }

    @Test
    void selectRandom_exclusionYieldsToSoleEligibleAspect() {
        List<Aspect> pool = List.of(aspect("fire", true, 10));
        AspectAssignmentService service = new AspectAssignmentService(() -> 0.0);

        Optional<Aspect> picked = service.selectRandom(pool, "fire");

        assertTrue(picked.isPresent());
        assertEquals("fire", picked.get().id());
    }

    @Test
    void selectRandom_noEligibleAspects_returnsEmpty() {
        List<Aspect> pool = List.of(aspect("fire", false, 10));
        AspectAssignmentService service = new AspectAssignmentService(() -> 0.0);

        assertTrue(service.selectRandom(pool, null).isEmpty());
    }

    @Test
    void selectRandom_respectsWeightProportions() {
        List<Aspect> pool = List.of(aspect("common", true, 80), aspect("rare", true, 20));
        AtomicInteger commonCount = new AtomicInteger();
        AtomicInteger rareCount = new AtomicInteger();
        java.util.Random random = new java.util.Random(42);
        AspectAssignmentService service = new AspectAssignmentService(random::nextDouble);

        for (int i = 0; i < 10_000; i++) {
            Aspect picked = service.selectRandom(pool, null).orElseThrow();
            if (picked.id().equals("common")) {
                commonCount.incrementAndGet();
            } else {
                rareCount.incrementAndGet();
            }
        }

        double commonRatio = commonCount.get() / 10_000.0;
        assertTrue(commonRatio > 0.75 && commonRatio < 0.85,
                "expected ~80% common picks, got " + commonRatio);
    }
}
