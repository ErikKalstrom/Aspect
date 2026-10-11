package com.aspects.smp.listener;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.Aspect;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
}
