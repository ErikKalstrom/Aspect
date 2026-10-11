package com.aspects.smp.listener;

import com.aspects.smp.ability.Ability;
import com.aspects.smp.ability.PassiveAbility;
import com.aspects.smp.aspect.Aspect;
import com.aspects.smp.aspect.AspectDefinition;
import com.aspects.smp.aspect.AspectRarity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void fallDamageMultiplier_returnsHalf_forVoidWithNoExplicitFlag() {
        Aspect voidAspect = aspect("void", Map.of());

        assertEquals(0.5, PlayerDamageListener.fallDamageMultiplier(voidAspect), 1e-9);
    }

    @Test
    void fallDamageMultiplier_returnsOne_forVoidWithFlagDisabled() {
        Aspect voidAspect = aspect("void", Map.of("passive.fall-damage-reduction", false));

        assertEquals(1.0, PlayerDamageListener.fallDamageMultiplier(voidAspect), 1e-9);
    }

    @Test
    void fallDamageMultiplier_respectsCustomReductionPercent() {
        Aspect voidAspect = aspect("void",
                Map.of("passive.fall-damage-reduction", true, "passive.reduction-percent", 30));

        assertEquals(0.7, PlayerDamageListener.fallDamageMultiplier(voidAspect), 1e-9);
    }

    @Test
    void fallDamageMultiplier_clampsAboveHundredPercent_toZeroMultiplier() {
        Aspect voidAspect = aspect("void",
                Map.of("passive.fall-damage-reduction", true, "passive.reduction-percent", 150));

        assertEquals(0.0, PlayerDamageListener.fallDamageMultiplier(voidAspect), 1e-9);
    }

    @Test
    void fallDamageMultiplier_clampsNegativePercent_toFullMultiplier() {
        Aspect voidAspect = aspect("void",
                Map.of("passive.fall-damage-reduction", true, "passive.reduction-percent", -20));

        assertEquals(1.0, PlayerDamageListener.fallDamageMultiplier(voidAspect), 1e-9);
    }

    @Test
    void fallDamageMultiplier_returnsOne_forFireEvenWithEmptySettings() {
        Aspect fire = aspect("fire", Map.of());

        assertEquals(1.0, PlayerDamageListener.fallDamageMultiplier(fire), 1e-9);
    }

    @Test
    void fallDamageMultiplier_returnsOne_forFrostEvenIfUnrelatedFlagEnabled() {
        Aspect frost = aspect("frost", Map.of("passive.freeze-immunity", true));

        assertEquals(1.0, PlayerDamageListener.fallDamageMultiplier(frost), 1e-9);
    }

    @Test
    void meleeDamageMultiplier_appliesPassiveBonus_forBloodWithNoBuffActive() {
        Aspect blood = aspect("blood", Map.of());

        assertEquals(1.1, PlayerDamageListener.meleeDamageMultiplier(blood, false), 1e-9);
    }

    @Test
    void meleeDamageMultiplier_appliesPassiveAndActiveBonus_whenBuffActive() {
        Aspect blood = aspect("blood", Map.of());

        assertEquals(1.3, PlayerDamageListener.meleeDamageMultiplier(blood, true), 1e-9);
    }

    @Test
    void meleeDamageMultiplier_returnsOne_whenPassiveFlagDisabledAndBuffInactive() {
        Aspect blood = aspect("blood", Map.of("passive.melee-damage-bonus", false));

        assertEquals(1.0, PlayerDamageListener.meleeDamageMultiplier(blood, false), 1e-9);
    }

    @Test
    void meleeDamageMultiplier_respectsCustomPercentages() {
        Aspect blood = aspect("blood",
                Map.of("passive.melee-damage-bonus-percent", 15, "active.bonus-percent", 25));

        assertEquals(1.40, PlayerDamageListener.meleeDamageMultiplier(blood, true), 1e-9);
    }

    @Test
    void meleeDamageMultiplier_returnsOne_forFireEvenWithBuffActiveTrue() {
        Aspect fire = aspect("fire", Map.of());

        assertEquals(1.0, PlayerDamageListener.meleeDamageMultiplier(fire, true), 1e-9);
    }
}
