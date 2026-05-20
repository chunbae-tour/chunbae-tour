package com.chunbaetour.domain.community.common;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class CursorUtils {

    private CursorUtils() {}

    public static String encode(long id) {
        String json = "{\"id\":" + id + "}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    public static long decode(String cursor) {
        try {
            byte[] decoded = Base64.getDecoder().decode(cursor);
            String json = new String(decoded, StandardCharsets.UTF_8);
            String value = json.replaceAll(".*\"id\"\\s*:\\s*(\\d+).*", "$1");
            return Long.parseLong(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor: " + cursor);
        }
    }
}
