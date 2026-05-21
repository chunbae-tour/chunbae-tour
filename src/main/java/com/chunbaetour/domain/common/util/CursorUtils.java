package com.chunbaetour.domain.common.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class CursorUtils {

    private CursorUtils() {}

    public static String encode(long id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Long.toString(id).getBytes(StandardCharsets.UTF_8));
    }

    public static long decode(String cursor) {
        try {
            return Long.parseLong(new String(
                    Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor: " + cursor);
        }
    }
}
