package com.aspects.smp.ability;

import com.aspects.smp.data.PlayerAspectData;
import com.aspects.smp.data.PlayerDataRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class FakePlayerDataRepository implements PlayerDataRepository {
    public final Map<UUID, PlayerAspectData> players = new ConcurrentHashMap<>();
    public final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    public final Map<UUID, Map<String, Integer>> mastery = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Optional<PlayerAspectData>> loadPlayer(UUID playerId) {
        return CompletableFuture.completedFuture(Optional.ofNullable(players.get(playerId)));
    }

    @Override
    public CompletableFuture<Void> savePlayer(PlayerAspectData data) {
        players.put(data.playerId(), data);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deletePlayer(UUID playerId) {
        players.remove(playerId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Map<String, Long>> loadCooldowns(UUID playerId) {
        return CompletableFuture.completedFuture(new HashMap<>(cooldowns.getOrDefault(playerId, Map.of())));
    }

    @Override
    public CompletableFuture<Void> saveCooldown(UUID playerId, String abilityId, long expiresAtMillis) {
        cooldowns.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>()).put(abilityId, expiresAtMillis);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteCooldowns(UUID playerId) {
        cooldowns.remove(playerId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Map<String, Integer>> loadMastery(UUID playerId) {
        return CompletableFuture.completedFuture(new HashMap<>(mastery.getOrDefault(playerId, Map.of())));
    }

    @Override
    public CompletableFuture<Integer> incrementMastery(UUID playerId, String aspectId) {
        int newCount = mastery.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>())
                .merge(aspectId, 1, Integer::sum);
        return CompletableFuture.completedFuture(newCount);
    }

    @Override
    public CompletableFuture<Void> deleteMastery(UUID playerId, String aspectId) {
        Map<String, Integer> playerMastery = mastery.get(playerId);
        if (playerMastery != null) {
            playerMastery.remove(aspectId);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteAllMastery(UUID playerId) {
        mastery.remove(playerId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() {
        // no-op
    }
}
