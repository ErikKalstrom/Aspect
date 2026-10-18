package com.aspects.smp.ability;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityCooldownManagerTest {

    @Test
    void isOnCooldown_falseBeforeAnyActivation() {
        AbilityCooldownManager manager = new AbilityCooldownManager(new FakePlayerDataRepository());
        assertFalse(manager.isOnCooldown(UUID.randomUUID(), "fire"));
    }

    @Test
    void startCooldown_thenIsOnCooldown_trueUntilExpiry() {
        AtomicLong clock = new AtomicLong(0L);
        AbilityCooldownManager manager = new AbilityCooldownManager(new FakePlayerDataRepository(), clock::get);
        UUID playerId = UUID.randomUUID();

        manager.startCooldown(playerId, "fire", 10);
        assertTrue(manager.isOnCooldown(playerId, "fire"));

        clock.set(9_000L);
        assertTrue(manager.isOnCooldown(playerId, "fire"));

        clock.set(10_001L);
        assertFalse(manager.isOnCooldown(playerId, "fire"));
    }

    @Test
    void startCooldown_persistsToRepository() {
        FakePlayerDataRepository repository = new FakePlayerDataRepository();
        AbilityCooldownManager manager = new AbilityCooldownManager(repository, () -> 0L);
        UUID playerId = UUID.randomUUID();

        manager.startCooldown(playerId, "fire", 25);

        assertEquals(25_000L, repository.cooldowns.get(playerId).get("fire"));
    }

    @Test
    void loadIntoCache_seedsCooldownsFromRepository() {
        AtomicLong clock = new AtomicLong(0L);
        AbilityCooldownManager manager = new AbilityCooldownManager(new FakePlayerDataRepository(), clock::get);
        UUID playerId = UUID.randomUUID();

        manager.loadIntoCache(playerId, java.util.Map.of("fire", 5_000L));

        assertTrue(manager.isOnCooldown(playerId, "fire"));
        clock.set(5_001L);
        assertFalse(manager.isOnCooldown(playerId, "fire"));
    }

    @Test
    void resetCooldowns_removesFromMemoryAndRepository() {
        FakePlayerDataRepository repository = new FakePlayerDataRepository();
        AbilityCooldownManager manager = new AbilityCooldownManager(repository, () -> 0L);
        UUID playerId = UUID.randomUUID();
        manager.startCooldown(playerId, "fire", 100);

        manager.resetCooldowns(playerId);

        assertFalse(manager.isOnCooldown(playerId, "fire"));
        assertFalse(repository.cooldowns.containsKey(playerId));
    }

    @Test
    void clearLocalCache_removesFromMemoryButKeepsRepositoryIntact() {
        FakePlayerDataRepository repository = new FakePlayerDataRepository();
        AbilityCooldownManager manager = new AbilityCooldownManager(repository, () -> 0L);
        UUID playerId = UUID.randomUUID();
        manager.startCooldown(playerId, "fire", 100);

        manager.clearLocalCache(playerId);

        assertFalse(manager.isOnCooldown(playerId, "fire"));
        assertEquals(100_000L, repository.cooldowns.get(playerId).get("fire"));
    }

    @Test
    void clearLocalCache_thenLoadIntoCache_restoresCooldownUnchanged() {
        FakePlayerDataRepository repository = new FakePlayerDataRepository();
        AtomicLong clock = new AtomicLong(0L);
        AbilityCooldownManager manager = new AbilityCooldownManager(repository, clock::get);
        UUID playerId = UUID.randomUUID();
        manager.startCooldown(playerId, "fire", 100);

        manager.clearLocalCache(playerId);
        manager.loadIntoCache(playerId, repository.cooldowns.get(playerId));

        assertTrue(manager.isOnCooldown(playerId, "fire"));
        clock.set(100_001L);
        assertFalse(manager.isOnCooldown(playerId, "fire"));
    }
}
