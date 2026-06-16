package com.chunbaetour.domain.common.config;

import com.chunbaetour.domain.report.interceptor.SanctionCheckInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final SanctionCheckInterceptor sanctionCheckInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sanctionCheckInterceptor)
                .addPathPatterns("/api/**")
                // 인증 경로는 제재 체크 불필요 (토큰 없는 요청)
                .excludePathPatterns(
                        "/api/v1/users/auth/**",
                        "/api/v1/merchants/auth/**",
                        "/api/v1/admin/auth/**",
                        "/api/v1/auth/**"
                );
    }
}
