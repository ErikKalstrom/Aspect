package com.aspects.smp.ability;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public final class TemporaryEffectManager {
    private final LongSupplier clock;
    private final Map<UUID, Map<String, Long>> expiryByPlayer = new ConcurrentHashMap<>();

    public TemporaryEffectManager() {
        this(System::currentTimeMillis);
    }

    public TemporaryEffectManager(LongSupplier clock) {
        this.clock = clock;
    }

    public void startBuff(UUID playerId, String buffId, long durationMillis) {
        long expiresAt = clock.getAsLong() + durationMillis;
        expiryByPlayer.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>()).put(buffId, expiresAt);
    }

    public boolean isActive(UUID playerId, String buffId) {
        Map<String, Long> map = expiryByPlayer.get(playerId);
        if (map == null) {
            return false;
        }
        Long expiry = map.get(buffId);
        return expiry != null && expiry > clock.getAsLong();
    }

    public void clear(UUID playerId) {
        expiryByPlayer.remove(playerId);
    }
}
