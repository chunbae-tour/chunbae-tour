package com.chunbaetour.domain.report.type;

public enum ReportAction {
    /** 콘텐츠 신고 처리 액션 (POST·COMMENT·REVIEW·USER) */
    WARNING, SUSPEND, DELETE, DISMISS,
    /** 가게 신고 처리 액션 (MERCHANT) */
    HIDE_SHOP, REVOKE_MERCHANT
}
