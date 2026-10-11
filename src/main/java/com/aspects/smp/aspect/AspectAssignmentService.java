package com.aspects.smp.aspect;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleSupplier;

public final class AspectAssignmentService {
    private final DoubleSupplier randomSource;

    public AspectAssignmentService() {
        this(Math::random);
    }

    public AspectAssignmentService(DoubleSupplier randomSource) {
        this.randomSource = randomSource;
    }

    public Optional<Aspect> selectRandom(List<Aspect> pool, String excludeId) {
        List<Aspect> eligible = new ArrayList<>();
        for (Aspect aspect : pool) {
            if (!aspect.definition().enabled()) {
                continue;
            }
            if (aspect.definition().weight() <= 0) {
                continue;
            }
            eligible.add(aspect);
        }

        if (eligible.size() > 1 && excludeId != null) {
            List<Aspect> withoutCurrent = new ArrayList<>();
            for (Aspect aspect : eligible) {
                if (!aspect.id().equals(excludeId)) {
                    withoutCurrent.add(aspect);
                }
            }
            if (!withoutCurrent.isEmpty()) {
                eligible = withoutCurrent;
            }
        }

        if (eligible.isEmpty()) {
            return Optional.empty();
        }

        double totalWeight = 0;
        for (Aspect aspect : eligible) {
            totalWeight += aspect.definition().weight();
        }

        double roll = randomSource.getAsDouble() * totalWeight;
        double cumulative = 0;
        for (Aspect aspect : eligible) {
            cumulative += aspect.definition().weight();
            if (roll < cumulative) {
                return Optional.of(aspect);
            }
        }
        return Optional.of(eligible.get(eligible.size() - 1));
    }
}
