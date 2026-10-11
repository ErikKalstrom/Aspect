package com.aspects.smp.ability;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporaryEffectManagerTest {

    @Test
    void isActive_falseBeforeAnyBuffStarted() {
        TemporaryEffectManager manager = new TemporaryEffectManager();

        assertFalse(manager.isActive(UUID.randomUUID(), "blood-pact"));
    }

    @Test
    void startBuff_thenIsActive_trueUntilExpiry() {
        AtomicLong clock = new AtomicLong(0L);
        TemporaryEffectManager manager = new TemporaryEffectManager(clock::get);
        UUID playerId = UUID.randomUUID();

        manager.startBuff(playerId, "blood-pact", 5_000L);
        assertTrue(manager.isActive(playerId, "blood-pact"));

        clock.set(4_999L);
        assertTrue(manager.isActive(playerId, "blood-pact"));

        clock.set(5_000L);
        assertFalse(manager.isActive(playerId, "blood-pact"));
    }

    @Test
    void startBuff_reactivating_extendsExpiryRatherThanStacking() {
        AtomicLong clock = new AtomicLong(0L);
        TemporaryEffectManager manager = new TemporaryEffectManager(clock::get);
        UUID playerId = UUID.randomUUID();

        manager.startBuff(playerId, "blood-pact", 5_000L);
        clock.set(3_000L);
        manager.startBuff(playerId, "blood-pact", 5_000L);

        clock.set(7_999L);
        assertTrue(manager.isActive(playerId, "blood-pact"));
        clock.set(8_000L);
        assertFalse(manager.isActive(playerId, "blood-pact"));
    }

    @Test
    void clear_removesActiveBuff() {
        TemporaryEffectManager manager = new TemporaryEffectManager();
        UUID playerId = UUID.randomUUID();
        manager.startBuff(playerId, "blood-pact", 10_000L);

        manager.clear(playerId);

        assertFalse(manager.isActive(playerId, "blood-pact"));
    }

    @Test
    void isActive_falseForDifferentBuffId() {
        TemporaryEffectManager manager = new TemporaryEffectManager();
        UUID playerId = UUID.randomUUID();
        manager.startBuff(playerId, "blood-pact", 10_000L);

        assertFalse(manager.isActive(playerId, "other-buff"));
    }
}
