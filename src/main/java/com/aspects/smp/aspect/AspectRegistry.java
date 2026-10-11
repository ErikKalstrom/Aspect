package com.aspects.smp.aspect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AspectRegistry {
    private final Map<String, Aspect> aspects = new LinkedHashMap<>();

    public void register(Aspect aspect) {
        aspects.put(aspect.id(), aspect);
    }

    public Optional<Aspect> get(String id) {
        return Optional.ofNullable(aspects.get(id));
    }

    public Collection<Aspect> all() {
        return Collections.unmodifiableCollection(aspects.values());
    }

    public List<Aspect> enabled() {
        List<Aspect> result = new ArrayList<>();
        for (Aspect aspect : aspects.values()) {
            if (aspect.definition().enabled()) {
                result.add(aspect);
            }
        }
        return result;
    }
}
