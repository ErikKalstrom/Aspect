package com.aspects.smp.aspect;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class AspectManager {
    private final Map<UUID, Aspect> assigned = new ConcurrentHashMap<>();

    public Optional<Aspect> getAspect(UUID playerId) {
        return Optional.ofNullable(assigned.get(playerId));
    }
}
