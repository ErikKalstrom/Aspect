package com.aspects.smp.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

public final class SoundUtil {
    private SoundUtil() {}

    public static void play(Player player, String soundName, float volume, float pitch, Logger logger) {
        if (soundName == null || soundName.isBlank()) {
            return;
        }
        NamespacedKey key = NamespacedKey.minecraft(soundName.toLowerCase());
        Sound sound = Registry.SOUNDS.get(key);
        if (sound == null) {
            logger.warning("Unknown sound '" + soundName + "' in configuration, skipping.");
            return;
        }
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}
