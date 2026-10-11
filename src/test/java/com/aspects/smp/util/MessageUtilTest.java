package com.aspects.smp.util;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageUtilTest {

    @Test
    void parse_rendersMiniMessageTagsAsPlainText() {
        var component = MessageUtil.parse("<red>Hello</red>");
        assertEquals("Hello", PlainTextComponentSerializer.plainText().serialize(component));
    }

    @Test
    void parse_substitutesPlaceholdersBeforeDeserializing() {
        var component = MessageUtil.parse("<white>Aspect: {aspect}</white>", Map.of("aspect", "Fire"));
        assertEquals("Aspect: Fire", PlainTextComponentSerializer.plainText().serialize(component));
    }

    @Test
    void parse_handlesNullGracefully() {
        var component = MessageUtil.parse(null);
        assertEquals("", PlainTextComponentSerializer.plainText().serialize(component));
    }
}
