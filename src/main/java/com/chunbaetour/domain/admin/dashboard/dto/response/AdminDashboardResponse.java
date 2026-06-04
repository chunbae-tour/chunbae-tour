package com.chunbaetour.domain.admin.dashboard.dto.response;

/**
 * 운영자 대시보드 카운트 요약 응답 (KAN-181, Admin Epic KAN-177 S03).
 *
 * <p><b>점진 패턴</b>: 모든 필드는 nullable wrapper({@link Long})로 둔다. 후속 슬라이스(S06 가게,
 * S10 콘텐츠)가 필드를 추가해도 기존 클라이언트는 새 필드를 무시하므로 호환이 깨지지 않는다.
 * 2차(S06)에서 가게/인증/상인신청 카운트 3종을 append한다.
 *
 * <p><b>캐시 호환</b>: 필드는 항상 뒤에 append하고 nullable로 둔다. 배포 직후 Redis에 남아있는
 * 구버전 JSON(3필드)을 역직렬화해도 추가 3필드는 null로 채워질 뿐 실패하지 않으며, TTL(1분) 경과 후
 * 6필드 JSON으로 자연 정합된다.
 *
 * @param totalUsers                   전체 사용자 수 (탈퇴 제외)
 * @param newUsersToday                오늘(한국 영업일) 신규 가입 수
 * @param suspendedUsers               정지 상태 사용자 수
 * @param totalShops                   전체 가게 수 (S06)
 * @param pendingCertifications        PENDING 인증 신청 수 (S06)
 * @param pendingMerchantApplications  PENDING 상인 신청 수 (S06)
 */
public record AdminDashboardResponse(
        Long totalUsers,
        Long newUsersToday,
        Long suspendedUsers,
        Long totalShops,
        Long pendingCertifications,
        Long pendingMerchantApplications
) {}
