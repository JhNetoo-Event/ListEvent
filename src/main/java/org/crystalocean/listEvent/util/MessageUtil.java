package org.crystalocean.listEvent.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private MessageUtil() {
    }

    public static Component toComponent(String text) {
        if (text == null) {
            return Component.empty();
        }

        // If the text contains MiniMessage tags, use MiniMessage,
        // otherwise, we can parse legacy ampersand colors
        if (text.contains("<") && text.contains(">")) {
             return MINI_MESSAGE.deserialize(text);
        } else {
             return LEGACY_SERIALIZER.deserialize(text);
        }
    }
}