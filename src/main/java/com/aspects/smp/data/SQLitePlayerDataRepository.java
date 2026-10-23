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
    /**
     * The database schema version this plugin version understands.
     *
     * <p><b>Convention — read this before changing table structure:</b> the {@code CREATE TABLE}
     * statements below are the permanent v1 shape and must never be edited directly to add a column
     * or change a type. {@code CREATE TABLE IF NOT EXISTS} only runs on a database that doesn't have
     * that table yet, so editing it silently does nothing on every existing installation's database
     * — only a brand-new one would ever see the change. Instead, bump this constant and add a branch
     * in {@link #migrateSchema()} keyed off the stored version (e.g. {@code if (storedVersion < 2) {
     * statement.execute("ALTER TABLE players ADD COLUMN ..."); }}), which runs for every database
     * that predates the bump — including both a genuinely fresh database (which starts at version 0,
     * same as one that predates versioning entirely) and an upgrading one, since there is nothing
     * meaningfully different between "fresh" and "legacy" once every structural change lives in a
     * migration step rather than in the base {@code CREATE TABLE} statements.
     */
    static final int CURRENT_SCHEMA_VERSION = 1;

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
                statement.execute("CREATE TABLE IF NOT EXISTS mastery (" +
                        "uuid TEXT NOT NULL, aspect_id TEXT NOT NULL, activations INTEGER NOT NULL, " +
                        "PRIMARY KEY (uuid, aspect_id))");
                statement.execute("CREATE TABLE IF NOT EXISTS schema_meta (version INTEGER NOT NULL)");
            }
            migrateSchema();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to open SQLite database at " + databaseFile, e);
        }
    }

    private void migrateSchema() throws SQLException {
        int storedVersion;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT version FROM schema_meta LIMIT 1")) {
            storedVersion = rs.next() ? rs.getInt("version") : 0;
        }
        if (storedVersion > CURRENT_SCHEMA_VERSION) {
            logger.warning("Player data schema version (" + storedVersion + ") is newer than this " +
                    "plugin version supports (" + CURRENT_SCHEMA_VERSION + ") -- was the plugin " +
                    "downgraded? Proceeding without migration; this may cause issues.");
            return;
        }
        if (storedVersion == CURRENT_SCHEMA_VERSION) {
            return;
        }
        // storedVersion == 0 (fresh database, or one that predates schema versioning) falls straight
        // through here with nothing to actually migrate. Future version bumps add a migration step
        // per version here, e.g.: if (storedVersion < 2) { ...; storedVersion = 2; }
        //
        // Run as one transaction: a real future migration step must commit atomically with the
        // version stamp below, or a crash between them would make the (already-applied) migration
        // re-run against already-migrated data on the next startup.
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM schema_meta");
            statement.executeUpdate("INSERT INTO schema_meta (version) VALUES (" + CURRENT_SCHEMA_VERSION + ")");
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
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
    public CompletableFuture<Void> deleteCooldowns(UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM cooldowns WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, playerId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.severe("Failed to delete cooldowns for " + playerId + ": " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Map<String, Integer>> loadMastery(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Integer> result = new HashMap<>();
            String sql = "SELECT aspect_id, activations FROM mastery WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.put(rs.getString("aspect_id"), rs.getInt("activations"));
                    }
                }
            } catch (SQLException e) {
                logger.severe("Failed to load mastery for " + playerId + ": " + e.getMessage());
            }
            return result;
        }, executor);
    }

    /**
     * Atomically increments and returns the new persisted count, rather than taking a count to
     * write: an absolute-write version of this method (as originally built) let the caller's
     * in-memory cache silently overwrite real historical data whenever that cache hadn't been
     * loaded first -- e.g. mastery tracking is turned off, a player joins while it's off (so
     * nothing loads their cache), then it's turned back on live and their very next activation
     * would otherwise reset their count to 1. Making the database the source of truth for the
     * increment itself removes that whole class of clobber, independent of cache state.
     */
    @Override
    public CompletableFuture<Integer> incrementMastery(UUID playerId, String aspectId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                try (PreparedStatement upsert = connection.prepareStatement(
                        "INSERT INTO mastery (uuid, aspect_id, activations) VALUES (?, ?, 1) " +
                                "ON CONFLICT(uuid, aspect_id) DO UPDATE SET activations = activations + 1")) {
                    upsert.setString(1, playerId.toString());
                    upsert.setString(2, aspectId);
                    upsert.executeUpdate();
                }
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT activations FROM mastery WHERE uuid = ? AND aspect_id = ?")) {
                    select.setString(1, playerId.toString());
                    select.setString(2, aspectId);
                    try (ResultSet rs = select.executeQuery()) {
                        return rs.next() ? rs.getInt("activations") : 1;
                    }
                }
            } catch (SQLException e) {
                logger.severe("Failed to increment mastery for " + playerId + "/" + aspectId + ": " + e.getMessage());
                return -1;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deleteMastery(UUID playerId, String aspectId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM mastery WHERE uuid = ? AND aspect_id = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, playerId.toString());
                ps.setString(2, aspectId);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.severe("Failed to delete mastery for " + playerId + ": " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deleteAllMastery(UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM mastery WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, playerId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.severe("Failed to delete all mastery for " + playerId + ": " + e.getMessage());
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
