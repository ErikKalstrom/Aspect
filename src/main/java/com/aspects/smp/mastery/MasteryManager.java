package com.aspects.smp.mastery;

import com.aspects.smp.data.PlayerDataRepository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks successful ability activations per (player, Aspect) pair, independent of which Aspect a
 * player currently has -- unlike {@code AbilityCooldownManager} or reroll/reset, mastery is never
 * cleared when a player changes Aspect, only when an admin explicitly resets it. Follows the same
 * memory-cache-plus-persistent-store shape as cooldowns, including the same quit/restart lesson: the
 * in-memory cache for an offline player is dropped on quit ({@link #clearLocalCache}), never deleted
 * from the database, and reloaded from the database on their next join ({@link #loadForPlayer}).
 */
public final class MasteryManager {
    private final PlayerDataRepository repository;
    private final Map<UUID, Map<String, Integer>> countsByPlayer = new ConcurrentHashMap<>();

    public MasteryManager(PlayerDataRepository repository) {
        this.repository = repository;
    }

    public void loadForPlayer(UUID playerId) {
        repository.loadMastery(playerId).thenAccept(counts -> loadIntoCache(playerId, counts));
    }

    void loadIntoCache(UUID playerId, Map<String, Integer> counts) {
        countsByPlayer.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>()).putAll(counts);
    }

    public void clearLocalCache(UUID playerId) {
        countsByPlayer.remove(playerId);
    }

    public int getActivationCount(UUID playerId, String aspectId) {
        Map<String, Integer> playerCounts = countsByPlayer.get(playerId);
        return playerCounts == null ? 0 : playerCounts.getOrDefault(aspectId, 0);
    }

    public Map<String, Integer> getAllCounts(UUID playerId) {
        return Map.copyOf(countsByPlayer.getOrDefault(playerId, Map.of()));
    }

    /** Records one more successful activation and persists the new total. Returns the new count. */
    public int recordActivation(UUID playerId, String aspectId) {
        Map<String, Integer> playerCounts = countsByPlayer.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>());
        int newCount = playerCounts.merge(aspectId, 1, Integer::sum);
        repository.saveMasteryCount(playerId, aspectId, newCount);
        return newCount;
    }

    /** Admin-triggered reset for a single Aspect: clears both the cache and the persisted count. */
    public void resetMastery(UUID playerId, String aspectId) {
        Map<String, Integer> playerCounts = countsByPlayer.get(playerId);
        if (playerCounts != null) {
            playerCounts.remove(aspectId);
        }
        repository.deleteMastery(playerId, aspectId);
    }

    /** Admin-triggered reset for every Aspect a player has mastery progress for. */
    public void resetAllMastery(UUID playerId) {
        countsByPlayer.remove(playerId);
        repository.deleteAllMastery(playerId);
    }
}
