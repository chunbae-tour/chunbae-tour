package com.chunbaetour.domain.common.response;

import com.chunbaetour.domain.common.util.CursorUtils;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToLongFunction;

public record CursorPageResponse<T>(
        List<T> content,
        String nextCursor,
        boolean hasNext,
        int size
) {

    /**
     * size+1 키워드 keyset 페이징 결과를 표준 응답으로 변환한다 (KAN-295).
     *
     * <p>여러 서비스에 흩어진 "다음 페이지 판별 → 잘라내기 → 매핑 → nextCursor 인코딩" 5줄 패턴을 공통화한다.
     * 호출 측은 {@code size + 1}개를 조회해 {@code raw}로 넘기고, 엔티티 매퍼와 커서용 id 추출자만 제공하면 된다.
     *
     * @param raw          {@code size + 1}개를 조회한 원본 목록(최신순 정렬 가정). 실제로는 size개까지만 노출된다.
     * @param size         페이지 크기. {@code raw.size() > size}이면 다음 페이지가 있다고 판단한다.
     * @param mapper       엔티티 → 응답 DTO 변환 함수
     * @param idExtractor  nextCursor 인코딩에 쓸 엔티티 id 추출 함수
     * @param <E>          원본 엔티티 타입
     * @param <R>          응답 DTO 타입
     * @return content(최대 size개), nextCursor(다음 없으면 null), hasNext, size로 구성된 응답
     */
    public static <E, R> CursorPageResponse<R> of(
            List<E> raw, int size, Function<E, R> mapper, ToLongFunction<E> idExtractor) {
        // size+1 조회 결과로 다음 페이지 존재 여부 판별 → 초과분을 잘라 실제 노출 페이지 구성
        boolean hasNext = raw.size() > size;
        List<E> page = hasNext ? raw.subList(0, size) : raw;

        // 엔티티 → 응답 DTO 변환
        List<R> content = page.stream().map(mapper).toList();

        // 다음 커서: 마지막 항목 id를 인코딩, 다음 페이지 없으면 null
        String nextCursor = hasNext
                ? CursorUtils.encode(idExtractor.applyAsLong(page.get(page.size() - 1)))
                : null;
        return new CursorPageResponse<>(content, nextCursor, hasNext, content.size());
    }
}
