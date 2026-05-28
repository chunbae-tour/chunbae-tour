package com.chunbaetour.domain.shop.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.shop.dto.response.SettlementResponse;
import com.chunbaetour.domain.shop.entity.Settlement;
import com.chunbaetour.domain.shop.type.SettlementStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class SettlementResponseMaskingTest {

    private static SettlementResponse buildResponse(String accountNumber) {
        Settlement s = Settlement.create(1L, 50_000L, "국민은행", accountNumber, "홍길동");
        ReflectionTestUtils.setField(s, "id", 1L);
        ReflectionTestUtils.setField(s, "status", SettlementStatus.PENDING);
        return SettlementResponse.from(s);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("계좌번호 마스킹 — 하이픈 유지, 앞 3자리·뒤 4자리 노출")
    @CsvSource({
        "123-4567-8901,   123-****-8901",   // 하이픈 포함
        "1234567890123,   123******0123",   // 하이픈 없음
        "12-3456-789012,  12-3***-**9012",  // 앞 구분자 다른 패턴
        "1234-5678-9012,  123*-****-9012",  // 하이픈 앞에 걸리는 경우
    })
    void maskAccountNumber(String input, String expected) {
        String masked = buildResponse(input.trim()).accountNumber();
        assertThat(masked).isEqualTo(expected.trim());
    }

    @ParameterizedTest(name = "길이 {0} → 마스킹 없이 그대로")
    @DisplayName("7자 이하 계좌번호 — 마스킹 미적용")
    @CsvSource({"1234567", "123-456", "12345"})
    void shortAccountNumber_noMask(String input) {
        assertThat(buildResponse(input).accountNumber()).isEqualTo(input);
    }
}
