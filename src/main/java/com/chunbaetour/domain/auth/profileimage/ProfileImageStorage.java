package com.chunbaetour.domain.auth.profileimage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 프로필 이미지 저장소 추상화 (KAN-320, shop E10 패턴 참고 — 코드 의존 X, auth 도메인 소유).
 * 구현체: {@code S3ProfileImageStorage}(@Profile("prod")) / {@code LocalDiskProfileImageStorage}(그 외).
 * 프로파일당 정확히 하나만 활성화된다.
 */
public interface ProfileImageStorage {

    /**
     * 검증 완료된 이미지를 저장하고 <b>객체 키</b>를 반환한다(예: {@code users/3/profile/uuid.jpg}).
     * 버킷이 비공개라 조회는 presigned GET으로 처리하며, 만료 URL을 DB에 저장하지 않기 위해 키만 영속화한다.
     */
    String upload(Long userId, MultipartFile file);

    /** 객체 키 → 조회용 presigned GET URL(만료 있음). S3=실제 presign, 로컬=passthrough. */
    String presignedGetUrl(String key);

    /**
     * 저장된 객체 삭제 — 프로필 교체 시 옛 객체 정리(유저당 1장이라 교체가 유일 고아 원천).
     * best-effort 계약: 실패해도 throw 안 함(구현체가 삼키고 로깅).
     */
    void delete(String key);
}
