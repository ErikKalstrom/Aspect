package com.aspects.smp.data;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLitePlayerDataRepositoryTest {

    private SQLitePlayerDataRepository newRepository() throws IOException {
        File dbFile = File.createTempFile("aspects-test", ".db");
        dbFile.deleteOnExit();
        return new SQLitePlayerDataRepository(dbFile, Logger.getLogger("AspectsTest"));
    }

    @Test
    void savePlayer_thenLoadPlayer_roundTrips() throws Exception {
        SQLitePlayerDataRepository repo = newRepository();
        UUID playerId = UUID.randomUUID();
        PlayerAspectData data = new PlayerAspectData(playerId, "fire", 12345L);

        repo.savePlayer(data).get();
        Optional<PlayerAspectData> loaded = repo.loadPlayer(playerId).get();

        assertTrue(loaded.isPresent());
        assertEquals("fire", loaded.get().aspectId());
        assertEquals(12345L, loaded.get().assignedAt());
        repo.close();
    }

    @Test
    void savePlayer_calledTwice_updatesInPlace() throws Exception {
        SQLitePlayerDataRepository repo = newRepository();
        UUID playerId = UUID.randomUUID();

        repo.savePlayer(new PlayerAspectData(playerId, "fire", 1L)).get();
        repo.savePlayer(new PlayerAspectData(playerId, "tide", 2L)).get();
        Optional<PlayerAspectData> loaded = repo.loadPlayer(playerId).get();

        assertTrue(loaded.isPresent());
        assertEquals("tide", loaded.get().aspectId());
        repo.close();
    }

    @Test
    void loadPlayer_unknownPlayer_returnsEmpty() throws Exception {
        SQLitePlayerDataRepository repo = newRepository();
        Optional<PlayerAspectData> loaded = repo.loadPlayer(UUID.randomUUID()).get();
        assertTrue(loaded.isEmpty());
        repo.close();
    }

    @Test
    void loadPlayer_whenUnderlyingConnectionIsBroken_completesExceptionally() throws Exception {
        // A transient DB failure (e.g. a locked file or disk hiccup) must surface as a failed
        // future, never silently as Optional.empty() — otherwise AspectManager can mistake it
        // for "new player" and overwrite a real saved Aspect. We force a SQLException here by
        // closing the connection out from under the repository (without shutting down its
        // executor, which is what the public close() method also does), so the DB worker thread
        // genuinely hits a SQLException instead of being rejected before it ever runs.
        SQLitePlayerDataRepository repo = newRepository();
        UUID playerId = UUID.randomUUID();

        Field connectionField = SQLitePlayerDataRepository.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        ((Connection) connectionField.get(repo)).close();

        assertThrows(ExecutionException.class, () -> repo.loadPlayer(playerId).get());
    }

    @Test
    void saveCooldown_thenLoadCooldowns_roundTrips() throws ExecutionException, InterruptedException, IOException {
        SQLitePlayerDataRepository repo = newRepository();
        UUID playerId = UUID.randomUUID();

        repo.saveCooldown(playerId, "fire", 999L).get();
        Map<String, Long> cooldowns = repo.loadCooldowns(playerId).get();

        assertEquals(1, cooldowns.size());
        assertEquals(999L, cooldowns.get("fire"));
        repo.close();
    }

    @Test
    void deletePlayer_removesStoredData() throws Exception {
        SQLitePlayerDataRepository repo = newRepository();
        UUID playerId = UUID.randomUUID();
        repo.savePlayer(new PlayerAspectData(playerId, "fire", 1L)).get();

        repo.deletePlayer(playerId).get();
        Optional<PlayerAspectData> loaded = repo.loadPlayer(playerId).get();

        assertTrue(loaded.isEmpty());
        repo.close();
    }

    @Test
    void freshDatabase_recordsCurrentSchemaVersion() throws Exception {
        File dbFile = File.createTempFile("aspects-test", ".db");
        dbFile.deleteOnExit();
        SQLitePlayerDataRepository repo = new SQLitePlayerDataRepository(dbFile, Logger.getLogger("AspectsTest"));

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT version FROM schema_meta")) {
            assertTrue(rs.next());
            assertEquals(SQLitePlayerDataRepository.CURRENT_SCHEMA_VERSION, rs.getInt("version"));
            assertEquals(false, rs.next(), "there must be exactly one schema_meta row");
        }
        repo.close();
    }

    @Test
    void reopeningExistingDatabase_doesNotDuplicateSchemaVersionRow() throws Exception {
        File dbFile = File.createTempFile("aspects-test", ".db");
        dbFile.deleteOnExit();
        new SQLitePlayerDataRepository(dbFile, Logger.getLogger("AspectsTest")).close();

        SQLitePlayerDataRepository repo = new SQLitePlayerDataRepository(dbFile, Logger.getLogger("AspectsTest"));

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) AS count FROM schema_meta")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("count"));
        }
        repo.close();
    }

    @Test
    void storedSchemaVersionNewerThanSupported_doesNotThrowAndLeavesVersionUnchanged() throws Exception {
        File dbFile = File.createTempFile("aspects-test", ".db");
        dbFile.deleteOnExit();
        int futureVersion = SQLitePlayerDataRepository.CURRENT_SCHEMA_VERSION + 98;
        new SQLitePlayerDataRepository(dbFile, Logger.getLogger("AspectsTest")).close();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.execute("UPDATE schema_meta SET version = " + futureVersion);
        }

        // Simulates a downgrade: the stored version is newer than this plugin build understands.
        // Must not throw -- this plugin's config/schema loading never crashes on an unexpected value.
        SQLitePlayerDataRepository repo = new SQLitePlayerDataRepository(dbFile, Logger.getLogger("AspectsTest"));

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT version FROM schema_meta")) {
            assertTrue(rs.next());
            assertEquals(futureVersion, rs.getInt("version"), "a newer stored version must be left untouched");
        }
        repo.close();
    }

    @Test
    void legacyDatabaseMissingSchemaMeta_isTreatedLikeAFreshDatabase() throws Exception {
        // A database created before schema versioning existed has the players/cooldowns tables but no
        // schema_meta row at all -- by this class's documented convention (see CURRENT_SCHEMA_VERSION's
        // javadoc), that must be handled identically to a genuinely fresh database: stamped at the
        // current version, since every real structural difference belongs in a migration step, not in
        // the base CREATE TABLE statements.
        File dbFile = File.createTempFile("aspects-test", ".db");
        dbFile.deleteOnExit();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE players (" +
                    "uuid TEXT PRIMARY KEY, aspect_id TEXT NOT NULL, assigned_at INTEGER NOT NULL)");
        }

        SQLitePlayerDataRepository repo = new SQLitePlayerDataRepository(dbFile, Logger.getLogger("AspectsTest"));

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT version FROM schema_meta")) {
            assertTrue(rs.next());
            assertEquals(SQLitePlayerDataRepository.CURRENT_SCHEMA_VERSION, rs.getInt("version"));
        }
        repo.close();
    }

    @Test
    void incrementMastery_thenLoadMastery_roundTrips() throws Exception {
        SQLitePlayerDataRepository repo = newRepository();
        UUID playerId = UUID.randomUUID();

        int newCount = repo.incrementMastery(playerId, "fire").get();
        Map<String, Integer> mastery = repo.loadMastery(playerId).get();

        assertEquals(1, newCount);
        assertEquals(1, mastery.size());
        assertEquals(1, mastery.get("fire"));
        repo.close();
    }

    @Test
    void incrementMastery_calledTwice_accumulatesInTheDatabase() throws Exception {
        SQLitePlayerDataRepository repo = newRepository();
        UUID playerId = UUID.randomUUID();

        int first = repo.incrementMastery(playerId, "fire").get();
        int second = repo.incrementMastery(playerId, "fire").get();
        Map<String, Integer> mastery = repo.loadMastery(playerId).get();

        assertEquals(1, first);
        assertEquals(2, second);
        assertEquals(2, mastery.get("fire"));
        repo.close();
    }

    @Test
    void deleteMastery_removesOnlyThatAspect() throws Exception {
        SQLitePlayerDataRepository repo = newRepository();
        UUID playerId = UUID.randomUUID();
        repo.incrementMastery(playerId, "fire").get();
        repo.incrementMastery(playerId, "frost").get();

        repo.deleteMastery(playerId, "fire").get();
        Map<String, Integer> mastery = repo.loadMastery(playerId).get();

        assertEquals(1, mastery.size());
        assertEquals(1, mastery.get("frost"));
        repo.close();
    }

    @Test
    void deleteAllMastery_removesEveryAspectForPlayer() throws Exception {
        SQLitePlayerDataRepository repo = newRepository();
        UUID playerId = UUID.randomUUID();
        repo.incrementMastery(playerId, "fire").get();
        repo.incrementMastery(playerId, "frost").get();

        repo.deleteAllMastery(playerId).get();
        Map<String, Integer> mastery = repo.loadMastery(playerId).get();

        assertTrue(mastery.isEmpty());
        repo.close();
    }
}
