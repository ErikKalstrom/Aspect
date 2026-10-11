package com.aspects.smp.listener;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.PassiveAbility;
import com.aspects.smp.aspect.Aspect;
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.aspect.AspectRarity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDamageListenerTest {

    private static final PassiveAbility NO_OP_PASSIVE = new PassiveAbility() {
        @Override
        public void onEquip(Player player, AspectDefinition definition) {
        }

        @Override
        public void onUnequip(Player player) {
        }
    };
    private static final Ability NO_OP_ACTIVE = context -> true;

    private Aspect aspect(String id, Map<String, Object> settings) {
        return new Aspect(
                new AspectDefinition(id, id, "", AspectRarity.COMMON, 10, true, settings),
                NO_OP_PASSIVE, NO_OP_ACTIVE);
    }

    @Test
    void cancelsFreezeDamage_returnsTrue_forFrostWithFlagEnabled() {
        Aspect frost = aspect("frost", Map.of("passive.freeze-immunity", true));

        assertTrue(PlayerDamageListener.cancelsFreezeDamage(frost));
    }

    @Test
    void cancelsFreezeDamage_returnsTrue_forFrostWithNoExplicitFlag() {
        Aspect frost = aspect("frost", Map.of());

        assertTrue(PlayerDamageListener.cancelsFreezeDamage(frost));
    }

    @Test
    void cancelsFreezeDamage_returnsFalse_forFrostWithFlagDisabled() {
        Aspect frost = aspect("frost", Map.of("passive.freeze-immunity", false));

        assertFalse(PlayerDamageListener.cancelsFreezeDamage(frost));
    }

    @Test
    void cancelsFreezeDamage_returnsFalse_forFireEvenWithEmptySettings() {
        Aspect fire = aspect("fire", Map.of());

        assertFalse(PlayerDamageListener.cancelsFreezeDamage(fire));
    }

    @Test
    void cancelsFreezeDamage_returnsFalse_forWindEvenWithEmptySettings() {
        Aspect wind = aspect("wind", Map.of());

        assertFalse(PlayerDamageListener.cancelsFreezeDamage(wind));
    }
}
