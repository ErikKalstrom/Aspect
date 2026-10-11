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

    void close();
}
