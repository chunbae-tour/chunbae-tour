package com.chunbaetour.domain.auth.profileimage;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * 정적 DTO 팩토리에서 프로필 이미지 표시 URL 변환을 쓰기 위한 브리지 (KAN-320).
 *
 * <p><b>왜 정적 브리지인가</b>: 프로필 이미지는 마이페이지뿐 아니라 커뮤니티 작성자({@code WriterInfo}), 채팅 멤버,
 * 관광지·동행 리뷰, 관리자 상세 등 여러 도메인의 <b>정적 record 팩토리</b>(예: {@code WriterInfo.from(account)})에서
 * 노출된다. 이들은 Spring 빈을 주입받을 수 없어 presign 변환기를 직접 못 쓴다. 각 팩토리·호출부·테스트를 모두
 * 바꾸지 않고(타 도메인 소유 코드 최소 변경) 변환을 적용하기 위한 의도적 정적 접근 지점이다.
 *
 * <p><b>안전 폴백</b>: 빈 초기화 전(순수 단위 테스트 등 Spring 컨텍스트 미부팅)에는 {@link #toDisplayUrl}가 입력을
 * 그대로 반환한다 → 기존 단위 테스트가 raw 값으로 통과하던 가정을 깨지 않는다. 운영/통합에서는 빈이 초기화되어
 * 우리 키는 presigned URL, 외부 URL은 passthrough로 변환된다({@link ProfileImageService#toDisplayUrl}).
 */
@Component
@RequiredArgsConstructor
public class ProfileImageDisplaySupport implements InitializingBean {

    private static volatile ProfileImageService delegate;

    private final ProfileImageService profileImageService;

    @Override
    public void afterPropertiesSet() {
        delegate = profileImageService;
    }

    /** 저장값 → 표시용 URL. 빈 미초기화 시 입력 그대로(폴백). */
    public static String toDisplayUrl(String stored) {
        ProfileImageService d = delegate;
        return d == null ? stored : d.toDisplayUrl(stored);
    }
}
