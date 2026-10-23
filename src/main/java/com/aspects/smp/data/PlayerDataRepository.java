package com.aspects.smp.data;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerDataRepository {
    CompletableFuture<Optional<PlayerAspectData>> loadPlayer(UUID playerId);

    CompletableFuture<Void> savePlayer(PlayerAspectData data);

    CompletableFuture<Void> deletePlayer(UUID playerId);

    CompletableFuture<Map<String, Long>> loadCooldowns(UUID playerId);

    CompletableFuture<Void> saveCooldown(UUID playerId, String abilityId, long expiresAtMillis);

    CompletableFuture<Void> deleteCooldowns(UUID playerId);

    CompletableFuture<Map<String, Integer>> loadMastery(UUID playerId);

    /** Atomically increments the persisted count by one and returns the new total (or a negative
     * value if the write failed), so the database -- not a possibly-unloaded in-memory cache -- is
     * always the source of truth for the actual count. */
    CompletableFuture<Integer> incrementMastery(UUID playerId, String aspectId);

    CompletableFuture<Void> deleteMastery(UUID playerId, String aspectId);

    CompletableFuture<Void> deleteAllMastery(UUID playerId);

    void close();
}
