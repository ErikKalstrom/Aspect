package com.aspects.smp.mastery;

import com.aspects.smp.ability.FakePlayerDataRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasteryManagerTest {

    @Test
    void recordActivation_incrementsAndPersists() {
        FakePlayerDataRepository repository = new FakePlayerDataRepository();
        MasteryManager manager = new MasteryManager(repository);
        UUID playerId = UUID.randomUUID();

        assertEquals(1, manager.recordActivation(playerId, "fire"));
        assertEquals(2, manager.recordActivation(playerId, "fire"));

        assertEquals(2, manager.getActivationCount(playerId, "fire"));
        assertEquals(2, repository.mastery.get(playerId).get("fire"));
    }

    @Test
    void recordActivation_tracksDifferentAspectsIndependently() {
        MasteryManager manager = new MasteryManager(new FakePlayerDataRepository());
        UUID playerId = UUID.randomUUID();

        manager.recordActivation(playerId, "fire");
        manager.recordActivation(playerId, "frost");
        manager.recordActivation(playerId, "frost");

        assertEquals(1, manager.getActivationCount(playerId, "fire"));
        assertEquals(2, manager.getActivationCount(playerId, "frost"));
    }

    @Test
    void getActivationCount_unknownPlayerOrAspect_returnsZero() {
        MasteryManager manager = new MasteryManager(new FakePlayerDataRepository());
        assertEquals(0, manager.getActivationCount(UUID.randomUUID(), "fire"));
    }

    @Test
    void clearLocalCache_dropsMemoryButKeepsPersistedData() {
        FakePlayerDataRepository repository = new FakePlayerDataRepository();
        MasteryManager manager = new MasteryManager(repository);
        UUID playerId = UUID.randomUUID();
        manager.recordActivation(playerId, "fire");

        manager.clearLocalCache(playerId);

        assertEquals(0, manager.getActivationCount(playerId, "fire"),
                "memory cache should be empty after clearing");
        assertEquals(1, repository.mastery.get(playerId).get("fire"),
                "the repository must be untouched -- mastery survives quit/restart, unlike cooldowns before they were fixed");
    }

    @Test
    void loadForPlayer_thenRecordActivation_resumesFromPersistedCount() {
        FakePlayerDataRepository repository = new FakePlayerDataRepository();
        UUID playerId = UUID.randomUUID();
        repository.mastery.put(playerId, new java.util.concurrent.ConcurrentHashMap<>(Map.of("fire", 10)));

        MasteryManager manager = new MasteryManager(repository);
        manager.loadForPlayer(playerId);

        assertEquals(10, manager.getActivationCount(playerId, "fire"));
        assertEquals(11, manager.recordActivation(playerId, "fire"));
    }

    @Test
    void resetMastery_removesOneAspectOnly() {
        FakePlayerDataRepository repository = new FakePlayerDataRepository();
        MasteryManager manager = new MasteryManager(repository);
        UUID playerId = UUID.randomUUID();
        manager.recordActivation(playerId, "fire");
        manager.recordActivation(playerId, "frost");

        manager.resetMastery(playerId, "fire");

        assertEquals(0, manager.getActivationCount(playerId, "fire"));
        assertEquals(1, manager.getActivationCount(playerId, "frost"));
        assertTrue(repository.mastery.getOrDefault(playerId, Map.of()).containsKey("frost"));
        assertTrue(!repository.mastery.getOrDefault(playerId, Map.of()).containsKey("fire"));
    }

    @Test
    void resetAllMastery_removesEveryAspect() {
        FakePlayerDataRepository repository = new FakePlayerDataRepository();
        MasteryManager manager = new MasteryManager(repository);
        UUID playerId = UUID.randomUUID();
        manager.recordActivation(playerId, "fire");
        manager.recordActivation(playerId, "frost");

        manager.resetAllMastery(playerId);

        assertEquals(Map.of(), manager.getAllCounts(playerId));
        assertTrue(repository.mastery.getOrDefault(playerId, Map.of()).isEmpty());
    }

    @Test
    void getAllCounts_returnsAllAspectsForPlayer() {
        MasteryManager manager = new MasteryManager(new FakePlayerDataRepository());
        UUID playerId = UUID.randomUUID();
        manager.recordActivation(playerId, "fire");
        manager.recordActivation(playerId, "frost");
        manager.recordActivation(playerId, "frost");

        Map<String, Integer> counts = manager.getAllCounts(playerId);

        assertEquals(Map.of("fire", 1, "frost", 2), counts);
    }
}
