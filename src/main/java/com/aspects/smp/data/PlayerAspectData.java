package com.aspects.smp.data;

import java.util.UUID;

public record PlayerAspectData(UUID playerId, String aspectId, long assignedAt) {
}
