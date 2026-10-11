package com.aspects.smp.listener;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.ability.impl.BloodActiveAbility;
import com.aspects.smp.aspect.Aspect;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public final class PlayerDamageListener implements Listener {
    private final AspectsPlugin plugin;

    public PlayerDamageListener(AspectsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Aspect aspect = plugin.getAspectManager().getAspect(player.getUniqueId()).orElse(null);
        if (aspect == null) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FREEZE) {
            if (cancelsFreezeDamage(aspect)) {
                event.setCancelled(true);
            }
        } else if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            double multiplier = fallDamageMultiplier(aspect);
            if (multiplier != 1.0) {
                event.setDamage(event.getDamage() * multiplier);
            }
        }
    }

    @EventHandler
    public void onMeleeDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) {
            return;
        }
        Aspect aspect = plugin.getAspectManager().getAspect(damager.getUniqueId()).orElse(null);
        if (aspect == null) {
            return;
        }
        boolean buffActive = plugin.getTemporaryEffectManager()
                .isActive(damager.getUniqueId(), BloodActiveAbility.BLOOD_PACT_BUFF_ID);
        double multiplier = meleeDamageMultiplier(aspect, buffActive);
        if (multiplier != 1.0) {
            event.setDamage(event.getDamage() * multiplier);
        }
    }

    static boolean cancelsFreezeDamage(Aspect aspect) {
        return aspect.id().equals("frost") && aspect.definition().getBoolean("passive.freeze-immunity", true);
    }

    static double fallDamageMultiplier(Aspect aspect) {
        if (!aspect.id().equals("void")) {
            return 1.0;
        }
        if (!aspect.definition().getBoolean("passive.fall-damage-reduction", true)) {
            return 1.0;
        }
        int reductionPercent = aspect.definition().getInt("passive.reduction-percent", 50);
        reductionPercent = Math.max(0, Math.min(100, reductionPercent));
        return 1.0 - (reductionPercent / 100.0);
    }

    static double meleeDamageMultiplier(Aspect aspect, boolean buffActive) {
        if (!aspect.id().equals("blood")) {
            return 1.0;
        }
        double multiplier = 1.0;
        if (aspect.definition().getBoolean("passive.melee-damage-bonus", true)) {
            int bonusPercent = Math.max(0, aspect.definition().getInt("passive.melee-damage-bonus-percent", 10));
            multiplier += bonusPercent / 100.0;
        }
        if (buffActive) {
            int activeBonusPercent = Math.max(0, aspect.definition().getInt("active.bonus-percent", 20));
            multiplier += activeBonusPercent / 100.0;
        }
        return multiplier;
    }
}
