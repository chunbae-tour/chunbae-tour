package com.chunbaetour.domain.search.type;

/**
 * 인기 검색어 순위 변동 타입.
 * <p>
 * SA 기능 명세서 F-SEARCH-002 기준:
 * 이전 순위({@code {search:ranking}:prev})와 현재 순위를 비교하여 결정한다.
 * </p>
 *
 * <ul>
 *   <li>{@link #UP}   — 이전 대비 순위가 상승한 경우</li>
 *   <li>{@link #DOWN} — 이전 대비 순위가 하락한 경우</li>
 *   <li>{@link #SAME} — 순위 변동 없음</li>
 *   <li>{@link #NEW}  — 이전 순위 목록에 없던 신규 진입어</li>
 * </ul>
 */
public enum RankingChangeType {

    /** 이전 대비 순위 상승 */
    UP,

    /** 이전 대비 순위 하락 */
    DOWN,

    /** 이전과 순위 동일 */
    SAME,

    /** 이전 목록에 없던 신규 진입 */
    NEW
}
