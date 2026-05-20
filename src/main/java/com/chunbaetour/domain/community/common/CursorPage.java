package com.chunbaetour.domain.community.common;

import java.util.List;

public record CursorPage<T>(
        List<T> content,
        String nextCursor,
        boolean hasNext,
        int size
) {}
