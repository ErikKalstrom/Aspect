package com.aspects.smp.data;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.sql.Connection;
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
}
