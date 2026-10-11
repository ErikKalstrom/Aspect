package com.aspects.smp.data;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class SQLitePlayerDataRepository implements PlayerDataRepository {
    private final Connection connection;
    private final ExecutorService executor;
    private final Logger logger;

    public SQLitePlayerDataRepository(File databaseFile, Logger logger) {
        this.logger = logger;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Aspects-DB");
            thread.setDaemon(true);
            return thread;
        });
        try {
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS players (" +
                        "uuid TEXT PRIMARY KEY, aspect_id TEXT NOT NULL, assigned_at INTEGER NOT NULL)");
                statement.execute("CREATE TABLE IF NOT EXISTS cooldowns (" +
                        "uuid TEXT NOT NULL, ability_id TEXT NOT NULL, expires_at INTEGER NOT NULL, " +
                        "PRIMARY KEY (uuid, ability_id))");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to open SQLite database at " + databaseFile, e);
        }
    }

    @Override
    public CompletableFuture<Optional<PlayerAspectData>> loadPlayer(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT aspect_id, assigned_at FROM players WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.<PlayerAspectData>empty();
                    }
                    return Optional.of(new PlayerAspectData(playerId, rs.getString("aspect_id"), rs.getLong("assigned_at")));
                }
            } catch (SQLException e) {
                logger.severe("Failed to load player data for " + playerId + ": " + e.getMessage());
                throw new CompletionException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> savePlayer(PlayerAspectData data) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO players (uuid, aspect_id, assigned_at) VALUES (?, ?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET aspect_id = excluded.aspect_id, assigned_at = excluded.assigned_at";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, data.playerId().toString());
                ps.setString(2, data.aspectId());
                ps.setLong(3, data.assignedAt());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.severe("Failed to save player data for " + data.playerId() + ": " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deletePlayer(UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM players WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, playerId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.severe("Failed to delete player data for " + playerId + ": " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Map<String, Long>> loadCooldowns(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Long> result = new HashMap<>();
            String sql = "SELECT ability_id, expires_at FROM cooldowns WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.put(rs.getString("ability_id"), rs.getLong("expires_at"));
                    }
                }
            } catch (SQLException e) {
                logger.severe("Failed to load cooldowns for " + playerId + ": " + e.getMessage());
            }
            return result;
        }, executor);
    }

    @Override
    public CompletableFuture<Void> saveCooldown(UUID playerId, String abilityId, long expiresAtMillis) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO cooldowns (uuid, ability_id, expires_at) VALUES (?, ?, ?) " +
                    "ON CONFLICT(uuid, ability_id) DO UPDATE SET expires_at = excluded.expires_at";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, playerId.toString());
                ps.setString(2, abilityId);
                ps.setLong(3, expiresAtMillis);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.severe("Failed to save cooldown for " + playerId + ": " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            connection.close();
        } catch (SQLException e) {
            logger.warning("Failed to close SQLite connection: " + e.getMessage());
        }
    }
}
