package com.chunbaetour.domain.translation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.common.repository.CommonErrorLogRepository;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import com.chunbaetour.domain.translation.client.TranslationClientException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * TranslationErrorLogWriter.save()의 REQUIRES_NEW 트랜잭션 전파 검증.
 * 단위 테스트(Mockito)는 Spring 컨텍스트 없이 실행되므로 실제 트랜잭션 동작 확인 불가.
 */
@SpringBootTest
class TranslationErrorLogWriterIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TranslationErrorLogWriter errorLogWriter;
    @Autowired private CommonErrorLogRepository commonErrorLogRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    // 테스트 시작 전 격리 — 다른 테스트가 남긴 로그로 count() 검증이 깨지는 것 방지
    @BeforeEach
    void setUp() {
        commonErrorLogRepository.deleteAll();
    }

    // 테스트 간 격리 — 저장된 로그 전체 삭제
    @AfterEach
    void cleanup() {
        commonErrorLogRepository.deleteAll();
    }

    // 외부 트랜잭션 롤백 시에도 REQUIRES_NEW로 독립 커밋 — 로그 유실 없음
    @Test
    void save_whenOuterTxRollsBack_logStillCommits() {
        TranslationClientException exception = new TranslationClientException("Google API 호출 실패");
        TransactionTemplate outer = new TransactionTemplate(transactionManager);

        // 외부 TX 시작 → save() 호출(REQUIRES_NEW 독립 커밋) → 강제 롤백
        assertThatThrownBy(() -> outer.execute(status -> {
            errorLogWriter.save(exception);
            throw new RuntimeException("강제 롤백");
        })).isInstanceOf(RuntimeException.class);

        // 외부 TX가 롤백됐어도 REQUIRES_NEW 로그는 커밋 상태
        assertThat(commonErrorLogRepository.count()).isEqualTo(1);
    }

    // 정상 흐름 — save() 성공 시 로그 1건 저장
    @Test
    void save_success_persistsLog() {
        TranslationClientException exception = new TranslationClientException("API 호출 실패");

        errorLogWriter.save(exception);

        assertThat(commonErrorLogRepository.count()).isEqualTo(1);
    }
}
