package com.aspects.smp.aspect;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.PassiveAbility;

public final class Aspect {
    private final AspectDefinition definition;
    private final PassiveAbility passive;
    private final Ability active;

    public Aspect(AspectDefinition definition, PassiveAbility passive, Ability active) {
        this.definition = definition;
        this.passive = passive;
        this.active = active;
    }

    public AspectDefinition definition() {
        return definition;
    }

    public PassiveAbility passive() {
        return passive;
    }

    public Ability active() {
        return active;
    }

    public String id() {
        return definition.id();
    }
}
