package com.aspects.smp.config;

import java.util.List;

public record GeneralConfig(
        boolean debug,
        String sqliteFile,
        boolean firstJoinEnabled,
        String firstJoinSound,
        String firstJoinParticle,
        int firstJoinParticleCount,
        List<String> blockedWorlds,
        boolean excludeCurrentOnReroll
) {
}
