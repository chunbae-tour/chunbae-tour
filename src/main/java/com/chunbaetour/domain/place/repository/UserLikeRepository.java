package com.chunbaetour.domain.place.repository;

import com.chunbaetour.domain.place.UserLike;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserLikeRepository extends JpaRepository<UserLike, Long> {

    boolean existsByUserIdAndPlaceId(Long userId, Long placeId);

    /**
     * 찜 취소 단일 쿼리.
     * SELECT(exists) → DELETE 이중 조회 없이 삭제된 row 수를 반환해
     * 0이면 LIKE_NOT_FOUND로 처리한다.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM UserLike ul WHERE ul.user.id = :userId AND ul.place.id = :placeId")
    int deleteByUserIdAndPlaceId(@Param("userId") Long userId, @Param("placeId") Long placeId);

    /**
     * 회원 탈퇴 시 사용자의 모든 찜 데이터를 일괄 hard delete (Epic C S2, KAN-144).
     *
     * <p>ADR ({@code docs/operations/account-withdrawal-policy.md} §2): UserLike는 보존 가치 낮은
     * 개인 행동 데이터이며, place.likeCount 캐시 컬럼이 별도로 유지되므로 통계에 영향이 없어 즉시 hard delete (B안).
     *
     * <p>호출자 = {@code UserMeService.deleteMe} (탈퇴 트랜잭션 내부). {@code AccountRepository.markAsDeleted}
     * CAS UPDATE가 1행을 영향(=호출자 1명만 성공)시킨 직후에만 본 메서드가 호출되므로 동시 탈퇴 race로 인한
     * 중복 실행은 DB가 직접 차단한다 (ADR §5 CAS UPDATE 채택).
     *
     * <p>{@code flushAutomatically = true} — 같은 트랜잭션 내 선행 UPDATE(markAsDeleted)의 dirty 상태를 본
     * DELETE 실행 전 강제 flush로 JPA 1차 캐시와 DB 동기 보장. {@code clearAutomatically = true} — bulk DELETE
     * 이후 영속성 컨텍스트의 stale UserLike 엔티티 제거(본 흐름에서는 fetch한 적 없지만 방어적).
     *
     * @param userId 삭제 대상 사용자 ID
     * @return 삭제된 row 수. 찜 없던 사용자도 0 반환으로 정상.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM UserLike ul WHERE ul.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    /** 마이페이지 연동용 (3-3): 사용자가 찜한 장소 목록 페이징 조회 */
    Page<UserLike> findByUserId(Long userId, Pageable pageable);

    /** 
     * 마이페이지 연동용 (3-3): 사용자가 찜한 '활성화된' 관광지 목록 페이징 조회 
     * N+1 문제를 방지하기 위해 @EntityGraph로 Place를 Fetch Join 처리합니다.
     */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "place")
    Page<UserLike> findByUserIdAndPlace_Status(Long userId, com.chunbaetour.domain.place.type.PlaceStatus status, Pageable pageable);
}
