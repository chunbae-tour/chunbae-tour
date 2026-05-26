package com.chunbaetour.domain.report.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.report.dto.response.ReportResponse;
import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.repository.ReportRepository;
import com.chunbaetour.domain.report.type.ReportStatus;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 서비스 (KAN-91 신고 목록 조회 / KAN-92 신고 처리 / KAN-93 자동 숨김).
 * AdminReportController → ReportService 직접 호출 (spec Section 3-1).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private static final Pattern CURSOR_PATTERN = Pattern.compile("^\\{\"id\":(\\d+)\\}$");
    private static final int AUTO_HIDE_THRESHOLD = 3;

    private final ReportRepository reportRepository;
    private final AccountRepository accountRepository;

    // ── KAN-91: 신고 목록 조회 ────────────────────────────────────────────

    /**
     * 관리자 신고 목록 cursor 페이징 조회.
     *
     * @param statusParam null = 전체, 그 외 = 해당 상태만
     * @param cursor      Base64 인코딩된 cursor (null = 첫 페이지)
     * @param size        페이지 크기
     */
    public CursorPageResponse<ReportResponse> getReports(String statusParam, String cursor, int size) {
        PageRequest pageable = PageRequest.of(0, size + 1);

        ReportStatus status = parseStatus(statusParam);
        List<Report> reports;

        if (status == null) {
            reports = (cursor == null)
                    ? reportRepository.findAllOrderByIdDesc(pageable)
                    : reportRepository.findByIdLessThanOrderByIdDesc(decodeCursor(cursor), pageable);
        } else {
            reports = (cursor == null)
                    ? reportRepository.findByStatusOrderByIdDesc(status, pageable)
                    : reportRepository.findByStatusAndIdLessThanOrderByIdDesc(status, decodeCursor(cursor), pageable);
        }

        boolean hasNext = reports.size() > size;
        List<Report> content = hasNext ? reports.subList(0, size) : reports;
        String nextCursor = hasNext ? encodeCursor(content.get(content.size() - 1).getId()) : null;

        List<ReportResponse> responses = content.stream()
                .map(r -> ReportResponse.of(r, resolveNickname(r.getReporterId())))
                .toList();

        return new CursorPageResponse<>(responses, nextCursor, hasNext, responses.size());
    }

    /**
     * 관리자 신고 단건 상세 조회.
     * AdminReportController 전용 — SecurityConfig에서 ADMIN 역할 보장.
     */
    public ReportResponse getReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        return ReportResponse.of(report, resolveNickname(report.getReporterId()));
    }

    /**
     * 사용자 신고 단건 조회 — 본인이 신고한 건만 허용.
     * ReportController(USER 전용) 에서 호출.
     *
     * @param reportId    조회할 신고 ID
     * @param requesterId 요청자 userId (@AuthenticationPrincipal)
     * @throws BusinessException REPORT_NOT_FOUND: 신고 없음
     * @throws BusinessException ACCESS_DENIED: 본인 신고 아님
     */
    public ReportResponse getMyReport(Long reportId, Long requesterId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        if (!report.getReporterId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return ReportResponse.of(report, resolveNickname(report.getReporterId()));
    }

    // ── 내부 유틸 ─────────────────────────────────────────────────────────

    /**
     * reporterId → 닉네임. 탈퇴 계정이면 "탈퇴한 사용자" 반환.
     */
    private String resolveNickname(Long reporterId) {
        return accountRepository.findById(reporterId)
                .map(Account::getNickname)
                .orElse("탈퇴한 사용자");
    }

    private ReportStatus parseStatus(String statusParam) {
        if (statusParam == null || statusParam.isBlank()) return null;
        try {
            return ReportStatus.valueOf(statusParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String encodeCursor(Long id) {
        String json = "{\"id\":" + id + "}";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private Long decodeCursor(String cursor) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            String json = new String(decoded, StandardCharsets.UTF_8);
            Matcher matcher = CURSOR_PATTERN.matcher(json);
            if (!matcher.matches()) throw new IllegalArgumentException("invalid cursor format");
            return Long.parseLong(matcher.group(1));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }
}
