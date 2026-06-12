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
     * @param size         페이지 크기(1 이상). {@code raw.size() > size}이면 다음 페이지가 있다고 판단한다.
     * @param mapper       엔티티 → 응답 DTO 변환 함수
     * @param idExtractor  nextCursor 인코딩에 쓸 엔티티 id 추출 함수
     * @param <E>          원본 엔티티 타입
     * @param <R>          응답 DTO 타입
     * @return content(최대 size개), nextCursor(다음 없으면 null), hasNext, 그리고 <b>요청 size를 그대로 echo</b>한
     *         size 필드로 구성된 응답. size는 실제 반환 개수(content.size())가 아니라 요청 페이지 크기다(팀 표준).
     * @throws IllegalArgumentException size가 1 미만인 경우
     */
    public static <E, R> CursorPageResponse<R> of(
            List<E> raw, int size, Function<E, R> mapper, ToLongFunction<E> idExtractor) {
        // 공통 진입점 fail-fast 가드 (KAN-295 리뷰): size=0이면 hasNext=true·page=[]가 되어 아래
        // page.get(page.size()-1) = get(-1)에서 IndexOutOfBoundsException(500). 호출자 @Min(1) 누락 시
        // 의미 불명의 500 대신 명시적 예외로 fail-fast 한다.
        if (size < 1) {
            throw new IllegalArgumentException("size must be >= 1: " + size);
        }

        // size+1 조회 결과로 다음 페이지 존재 여부 판별 → 초과분을 잘라 실제 노출 페이지 구성
        boolean hasNext = raw.size() > size;
        List<E> page = hasNext ? raw.subList(0, size) : raw;

        // 엔티티 → 응답 DTO 변환
        List<R> content = page.stream().map(mapper).toList();

        // 다음 커서: 마지막 항목 id를 인코딩, 다음 페이지 없으면 null
        String nextCursor = hasNext
                ? CursorUtils.encode(idExtractor.applyAsLong(page.get(page.size() - 1)))
                : null;
        // size 필드는 실제 반환 개수가 아니라 요청 size를 그대로 echo한다(팀 표준).
        return new CursorPageResponse<>(content, nextCursor, hasNext, size);
    }
}
