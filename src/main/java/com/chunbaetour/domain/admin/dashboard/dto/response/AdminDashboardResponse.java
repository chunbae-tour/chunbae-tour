package com.chunbaetour.domain.admin.dashboard.dto.response;

/**
 * 운영자 대시보드 카운트 요약 응답 (KAN-181, Admin Epic KAN-177 S03).
 *
 * <p><b>점진 패턴</b>: 모든 필드는 nullable wrapper({@link Long})로 둔다. 후속 슬라이스(S06 가게,
 * S10 콘텐츠)가 필드를 추가해도 기존 클라이언트는 새 필드를 무시하므로 호환이 깨지지 않는다.
 * 1차(본 슬라이스)는 사용자 카운트 3종만 노출한다.
 *
 * @param totalUsers     전체 사용자 수 (탈퇴 제외)
 * @param newUsersToday  오늘(한국 영업일) 신규 가입 수
 * @param suspendedUsers 정지 상태 사용자 수
 */
public record AdminDashboardResponse(
        Long totalUsers,
        Long newUsersToday,
        Long suspendedUsers
) {}
