package com.chunbaetour.domain.community.common;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CursorUtils {

    private static final Pattern ID_PATTERN = Pattern.compile("\"id\":(\\d+)");

    private CursorUtils() {}

    public static String encode(long id) {
        String json = "{\"id\":" + id + "}";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    public static long decode(String cursor) {
        byte[] decoded = Base64.getUrlDecoder().decode(cursor);
        String json = new String(decoded, StandardCharsets.UTF_8);
        Matcher matcher = ID_PATTERN.matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid cursor: " + cursor);
        }
        return Long.parseLong(matcher.group(1));
    }
}
