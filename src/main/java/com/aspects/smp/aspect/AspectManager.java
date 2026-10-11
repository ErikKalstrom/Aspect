package com.aspects.smp.aspect;

import com.aspects.smp.ability.AbilityCooldownManager;
import com.aspects.smp.data.PlayerAspectData;
import com.aspects.smp.data.PlayerDataRepository;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

public final class AspectManager {
    private final AspectRegistry registry;
    private final AspectAssignmentService assignmentService;
    private final PlayerDataRepository repository;
    private final AbilityCooldownManager cooldownManager;
    private final Logger logger;
    private final boolean excludeCurrentOnReroll;
    private final Executor mainThreadExecutor;

    private final Map<UUID, Aspect> assigned = new ConcurrentHashMap<>();

    public AspectManager(AspectRegistry registry, AspectAssignmentService assignmentService,
                          PlayerDataRepository repository, AbilityCooldownManager cooldownManager,
                          Logger logger, boolean excludeCurrentOnReroll, Executor mainThreadExecutor) {
        this.registry = registry;
        this.assignmentService = assignmentService;
        this.repository = repository;
        this.cooldownManager = cooldownManager;
        this.logger = logger;
        this.excludeCurrentOnReroll = excludeCurrentOnReroll;
        this.mainThreadExecutor = mainThreadExecutor;
    }

    public Optional<Aspect> getAspect(UUID playerId) {
        return Optional.ofNullable(assigned.get(playerId));
    }

    public void loadOrAssign(Player player, Runnable onFirstJoin) {
        UUID id = player.getUniqueId();
        repository.loadPlayer(id)
                .thenCombine(repository.loadCooldowns(id), Map::entry)
                .thenAccept(loaded -> mainThreadExecutor.execute(() -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    Optional<PlayerAspectData> existing = loaded.getKey();
                    Map<String, Long> cooldowns = loaded.getValue();
                    if (existing.isPresent()) {
                        Aspect aspect = registry.get(existing.get().aspectId()).orElse(null);
                        if (aspect == null || !aspect.definition().enabled()) {
                            logger.warning("Player " + player.getName() + " had an invalid or disabled aspect '" +
                                    existing.get().aspectId() + "'; reassigning.");
                            for (Aspect candidate : registry.all()) {
                                candidate.passive().onUnequip(player);
                            }
                            assignNew(player, null, onFirstJoin);
                            return;
                        }
                        cooldownManager.loadIntoCache(id, cooldowns);
                        assigned.put(id, aspect);
                        aspect.passive().onEquip(player, aspect.definition());
                    } else {
                        assignNew(player, null, onFirstJoin);
                    }
                }))
                .exceptionally(ex -> {
                    logger.severe("Failed to load Aspect data for " + player.getName() +
                            "; leaving them unassigned for this session: " + ex.getMessage());
                    return null;
                });
    }

    private void assignNew(Player player, String excludeId, Runnable onAssigned) {
        Optional<Aspect> picked = assignmentService.selectRandom(new ArrayList<>(registry.enabled()), excludeId);
        if (picked.isEmpty()) {
            logger.severe("No eligible Aspect could be assigned to " + player.getName() +
                    " — check that at least one Aspect is enabled with a positive weight.");
            return;
        }
        Aspect aspect = picked.get();
        assigned.put(player.getUniqueId(), aspect);
        repository.savePlayer(new PlayerAspectData(player.getUniqueId(), aspect.id(), System.currentTimeMillis()));
        aspect.passive().onEquip(player, aspect.definition());
        if (onAssigned != null) {
            onAssigned.run();
        }
    }

    public void reapplyPassive(Player player) {
        Aspect aspect = assigned.get(player.getUniqueId());
        if (aspect != null) {
            aspect.passive().onEquip(player, aspect.definition());
        }
    }

    public void unload(Player player) {
        Aspect aspect = assigned.remove(player.getUniqueId());
        if (aspect != null) {
            aspect.passive().onUnequip(player);
        }
        cooldownManager.clearCache(player.getUniqueId());
    }

    public Optional<Aspect> adminSet(Player player, String aspectId) {
        Aspect aspect = registry.get(aspectId).orElse(null);
        if (aspect == null || !aspect.definition().enabled()) {
            return Optional.empty();
        }
        Aspect previous = assigned.get(player.getUniqueId());
        if (previous != null) {
            previous.passive().onUnequip(player);
        }
        assigned.put(player.getUniqueId(), aspect);
        repository.savePlayer(new PlayerAspectData(player.getUniqueId(), aspect.id(), System.currentTimeMillis()));
        cooldownManager.clearCache(player.getUniqueId());
        aspect.passive().onEquip(player, aspect.definition());
        return Optional.of(aspect);
    }

    public Optional<Aspect> adminReroll(Player player) {
        Aspect previous = assigned.get(player.getUniqueId());
        String excludeId = excludeCurrentOnReroll && previous != null ? previous.id() : null;
        Optional<Aspect> picked = assignmentService.selectRandom(new ArrayList<>(registry.enabled()), excludeId);
        if (picked.isEmpty()) {
            return Optional.empty();
        }
        if (previous != null) {
            previous.passive().onUnequip(player);
        }
        Aspect aspect = picked.get();
        assigned.put(player.getUniqueId(), aspect);
        repository.savePlayer(new PlayerAspectData(player.getUniqueId(), aspect.id(), System.currentTimeMillis()));
        cooldownManager.clearCache(player.getUniqueId());
        aspect.passive().onEquip(player, aspect.definition());
        return Optional.of(aspect);
    }
}
