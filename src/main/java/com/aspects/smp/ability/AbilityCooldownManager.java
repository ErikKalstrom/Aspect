package com.aspects.smp.ability;

import com.aspects.smp.data.PlayerDataRepository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public final class AbilityCooldownManager {
    private final PlayerDataRepository repository;
    private final LongSupplier clock;
    private final Map<UUID, Map<String, Long>> expiryByPlayer = new ConcurrentHashMap<>();

    public AbilityCooldownManager(PlayerDataRepository repository) {
        this(repository, System::currentTimeMillis);
    }

    public AbilityCooldownManager(PlayerDataRepository repository, LongSupplier clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void loadIntoCache(UUID playerId, Map<String, Long> cooldowns) {
        Map<String, Long> playerCooldowns = expiryByPlayer.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>());
        for (Map.Entry<String, Long> entry : cooldowns.entrySet()) {
            playerCooldowns.merge(entry.getKey(), entry.getValue(), Math::max);
        }
    }

    /**
     * Drops the in-memory cooldown cache for a player without touching persisted state.
     * Use this when a player goes offline (quit, server shutdown) — cooldowns must survive
     * to be reloaded via {@link #loadIntoCache} on their next join, not be erased.
     */
    public void clearLocalCache(UUID playerId) {
        expiryByPlayer.remove(playerId);
    }

    /**
     * Wipes a player's cooldowns everywhere (memory and persisted storage). Use this only for
     * an intentional full reset — admin "clear cooldown", or assigning a new Aspect.
     */
    public void resetCooldowns(UUID playerId) {
        expiryByPlayer.remove(playerId);
        repository.deleteCooldowns(playerId);
    }

    public boolean isOnCooldown(UUID playerId, String abilityId) {
        return remainingMillis(playerId, abilityId) > 0;
    }

    public long remainingMillis(UUID playerId, String abilityId) {
        Map<String, Long> map = expiryByPlayer.get(playerId);
        if (map == null) {
            return 0;
        }
        Long expiry = map.get(abilityId);
        if (expiry == null) {
            return 0;
        }
        return Math.max(0, expiry - clock.getAsLong());
    }

    public void startCooldown(UUID playerId, String abilityId, long durationSeconds) {
        long expiresAt = clock.getAsLong() + durationSeconds * 1000L;
        expiryByPlayer.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>()).put(abilityId, expiresAt);
        repository.saveCooldown(playerId, abilityId, expiresAt);
    }
}
