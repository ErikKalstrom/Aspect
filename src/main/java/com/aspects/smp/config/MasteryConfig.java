package com.aspects.smp.config;

import java.util.List;

public record MasteryConfig(boolean enabled, List<MasteryLevel> levels) {
}
