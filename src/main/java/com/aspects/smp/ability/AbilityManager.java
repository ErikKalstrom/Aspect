package com.aspects.smp.ability;

import com.aspects.smp.AspectsPlugin;
import com.aspects.smp.aspect.Aspect;
import com.aspects.smp.aspect.AspectManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

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
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            return ActivationResult.failure("You cannot use abilities right now.");
        }

        Aspect aspect = aspectManager.getAspect(player.getUniqueId()).orElse(null);
        if (aspect == null) {
            return ActivationResult.failure("You do not have an Aspect assigned yet.");
        }
        if (!aspect.definition().enabled()) {
            return ActivationResult.failure("Your Aspect is currently disabled on this server.");
        }
        if (!protectionGate.isAllowed(player)) {
            return ActivationResult.failure("Abilities cannot be used in this world.");
        }
        if (cooldownManager.isOnCooldown(player.getUniqueId(), aspect.id())) {
            long seconds = (cooldownManager.remainingMillis(player.getUniqueId(), aspect.id()) + 999) / 1000;
            return ActivationResult.failure("Your ability is on cooldown for " + seconds + "s.");
        }

        AbilityContext context = new AbilityContext(player, aspect, plugin);
        boolean activated = aspect.active().activate(context);
        if (!activated) {
            return ActivationResult.failure("Your ability could not be activated right now.");
        }

        long cooldownSeconds = (long) aspect.definition().getDouble("active.cooldown-seconds", 20.0);
        cooldownManager.startCooldown(player.getUniqueId(), aspect.id(), cooldownSeconds);
        return ActivationResult.success();
    }

    public record ActivationResult(boolean isSuccess, String failureMessage) {
        public static ActivationResult success() {
            return new ActivationResult(true, null);
        }

        public static ActivationResult failure(String message) {
            return new ActivationResult(false, message);
        }
    }
}
