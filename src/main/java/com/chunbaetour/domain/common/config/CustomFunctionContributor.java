package com.chunbaetour.domain.common.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;

/**
 * Hibernate 6 환경에서 커스텀 SQL 함수를 등록하기 위한 Contributor.
 * QueryDSL 등에서 MATCH AGAINST 같은 특정 DB 종속 함수를 사용하기 위해 필요합니다.
 */
public class CustomFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        // MySQL Full-Text Search (Boolean Mode) 함수 등록
        // QueryDSL 사용 예: Expressions.numberTemplate(Double.class, "function('match_against', {0}, {1})", place.name, keyword)
        functionContributions.getFunctionRegistry()
                .registerPattern("match_against", "MATCH(?1) AGAINST(?2 IN BOOLEAN MODE)");
    }
}
