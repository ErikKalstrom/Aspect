package com.aspects.smp.data;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
