package com.aspects.smp.mastery;

import com.aspects.smp.data.PlayerDataRepository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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

    /**
     * Reads a player's mastery directly from the database, bypassing the in-memory cache entirely.
     * Used for admin inspection, where showing stale or empty data from a cache that was never
     * loaded (e.g. the player is online but joined while mastery tracking was off) could lead an
     * admin to make a reset decision based on wrong information -- unlike the player-facing displays
     * in {@code /aspects info} and the main GUI, an admin command can tolerate the extra DB round
     * trip in exchange for always being correct.
     */
    public CompletableFuture<Map<String, Integer>> loadFromDatabase(UUID playerId) {
        return repository.loadMastery(playerId);
    }

    /**
     * Records one more successful activation. The database performs the increment atomically and
     * is the sole source of truth for the resulting count -- this deliberately does NOT increment
     * the local cache itself and write that value, because a cache that was never loaded (e.g. the
     * player joined while mastery tracking was off, then it was turned back on with a live reload)
     * would otherwise compute "1" and silently overwrite real historical data in the database. The
     * cache is only ever updated here with whatever the database reports back as the true count,
     * which self-heals an unloaded or stale cache on every single activation.
     */
    public void recordActivation(UUID playerId, String aspectId) {
        repository.incrementMastery(playerId, aspectId).thenAccept(newCount -> {
            if (newCount < 0) {
                return; // the write failed and was already logged; leave the cache as-is rather than guess
            }
            countsByPlayer.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>()).put(aspectId, newCount);
        });
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
