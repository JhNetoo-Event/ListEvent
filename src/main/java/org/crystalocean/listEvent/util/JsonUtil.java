package org.crystalocean.listEvent.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;

import java.time.Instant;

public final class JsonUtil {

    private JsonUtil() {
    }

    public static Gson createGson(boolean prettyPrint) {
        GsonBuilder builder = new GsonBuilder()
                .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>)
                        (src, typeOfSrc, context) -> context.serialize(src.toString()))
                .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>)
                        (json, typeOfT, context) -> Instant.parse(json.getAsString()));

        if (prettyPrint) {
            builder.setPrettyPrinting();
        }

        return builder.create();
    }
}