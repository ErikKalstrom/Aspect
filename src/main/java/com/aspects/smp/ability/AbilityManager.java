package com.aspects.smp.ability;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.Aspect;
import com.aspects.smp.aspect.AspectManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Map;

public final class AbilityManager {
    private final AspectManager aspectManager;
    private final AbilityCooldownManager cooldownManager;
    private final ProtectionGate protectionGate;
    private final AspectsPlugin plugin;

    public AbilityManager(AspectManager aspectManager, AbilityCooldownManager cooldownManager,
                           ProtectionGate protectionGate, AspectsPlugin plugin) {
        this.aspectManager = aspectManager;
        this.cooldownManager = cooldownManager;
        this.protectionGate = protectionGate;
        this.plugin = plugin;
    }

    public ActivationResult tryActivate(Player player) {
        if (!player.hasPermission("aspects.use")) {
            return ActivationResult.failed("ability.no-permission");
        }
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            return ActivationResult.failed("ability.unavailable");
        }

        Aspect aspect = aspectManager.getAspect(player.getUniqueId()).orElse(null);
        if (aspect == null) {
            return ActivationResult.failed("ability.no-aspect");
        }
        if (!aspect.definition().enabled()) {
            return ActivationResult.failed("ability.aspect-disabled");
        }
        if (!protectionGate.isAllowed(player)) {
            return ActivationResult.failed("ability.world-blocked");
        }
        if (cooldownManager.isOnCooldown(player.getUniqueId(), aspect.id())) {
            long seconds = (cooldownManager.remainingMillis(player.getUniqueId(), aspect.id()) + 999) / 1000;
            return ActivationResult.failed("ability.on-cooldown", Map.of("seconds", String.valueOf(seconds)));
        }

        AbilityContext context = new AbilityContext(player, aspect, plugin);
        boolean activated = aspect.active().activate(context);
        if (!activated) {
            return ActivationResult.failed("ability.activation-failed");
        }

        long cooldownSeconds = (long) aspect.definition().getDouble("active.cooldown-seconds", 20.0);
        cooldownManager.startCooldown(player.getUniqueId(), aspect.id(), cooldownSeconds);
        return ActivationResult.succeeded();
    }

    public record ActivationResult(boolean success, String messageKey, Map<String, String> placeholders) {
        public static ActivationResult succeeded() {
            return new ActivationResult(true, null, Map.of());
        }

        public static ActivationResult failed(String messageKey) {
            return new ActivationResult(false, messageKey, Map.of());
        }

        public static ActivationResult failed(String messageKey, Map<String, String> placeholders) {
            return new ActivationResult(false, messageKey, placeholders);
        }
    }
}
