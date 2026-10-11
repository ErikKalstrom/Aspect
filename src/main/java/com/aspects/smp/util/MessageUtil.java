package com.aspects.smp.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.Map;

public final class MessageUtil {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private MessageUtil() {}

    public static Component parse(String raw) {
        return MINI_MESSAGE.deserialize(raw == null ? "" : raw);
    }

    public static Component parse(String raw, Map<String, String> placeholders) {
        String result = raw == null ? "" : raw;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return MINI_MESSAGE.deserialize(result);
    }

    public static void sendMessage(Player player, String raw) {
        player.sendMessage(parse(raw));
    }

    public static void sendMessage(Player player, String raw, Map<String, String> placeholders) {
        player.sendMessage(parse(raw, placeholders));
    }

    public static void sendActionBar(Player player, String raw) {
        player.sendActionBar(parse(raw));
    }

    public static void sendActionBar(Player player, String raw, Map<String, String> placeholders) {
        player.sendActionBar(parse(raw, placeholders));
    }
}
