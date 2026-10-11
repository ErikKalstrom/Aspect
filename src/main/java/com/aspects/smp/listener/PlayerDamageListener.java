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
        if (event.getCause() != EntityDamageEvent.DamageCause.FREEZE) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Aspect aspect = plugin.getAspectManager().getAspect(player.getUniqueId()).orElse(null);
        if (aspect == null) {
            return;
        }
        if (cancelsFreezeDamage(aspect)) {
            event.setCancelled(true);
        }
    }

    static boolean cancelsFreezeDamage(Aspect aspect) {
        return aspect.id().equals("frost") && aspect.definition().getBoolean("passive.freeze-immunity", true);
    }
}
