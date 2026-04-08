package org.crystalocean.listEvent.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private MessageUtil() {
    }

    public static long parseTime(String time) {
        if (time == null || time.isEmpty()) return 0;
        long duration = 0;
        StringBuilder number = new StringBuilder();
        for (char c : time.toCharArray()) {
            if (Character.isDigit(c)) {
                number.append(c);
            } else {
                long val = number.length() > 0 ? Long.parseLong(number.toString()) : 0;
                switch (Character.toLowerCase(c)) {
                    case 's' -> duration += val;
                    case 'm' -> duration += val * 60;
                    case 'h' -> duration += val * 3600;
                    case 'd' -> duration += val * 86400;
                    case 'w' -> duration += val * 604800;
                    case 'y' -> duration += val * 31536000;
                }
                number = new StringBuilder();
            }
        }
        return duration * 1000; // milliseconds
    }

    public static Component toComponent(String text) {
        if (text == null) {
            return Component.empty();
        }

        if (text.contains("<") && text.contains(">")) {
            return MINI_MESSAGE.deserialize(text);
        } else {
            return LEGACY_SERIALIZER.deserialize(text);
        }
    }
}