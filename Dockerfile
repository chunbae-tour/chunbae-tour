# syntax=docker/dockerfile:1
#
# 운영 배포용 멀티스테이지 이미지 (배포 S1, KAN-223).
# builder(JDK로 bootJar 빌드) → runtime(JRE alpine, non-root, graceful/healthcheck)로 분리해
# 최종 이미지에 빌드 도구·소스를 남기지 않는다(작은 이미지 + 공격면 축소).

# ===== Stage 1: builder =====
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

# 1) 의존성 캐시 레이어 — gradle 설정 파일만 먼저 복사.
#    build.gradle/settings.gradle가 안 바뀌면 이 레이어가 캐시돼 의존성 재다운로드를 건너뛴다.
#    lombok.config는 컴파일 시점에 프로젝트 루트에서 읽혀 @Lazy 등 copyableAnnotations를 생성자에 복사한다.
#    누락 시 Docker 산출물만 @Lazy가 빠져(예: WebSocket/STOMP 순환 의존 방지 무력화) 런타임 컨텍스트 로드가 깨진다.
COPY gradlew settings.gradle build.gradle lombok.config ./
COPY gradle ./gradle
# dependencies는 캐시 워밍 용도라 실패해도 빌드를 막지 않는다(|| true) — 실제 의존성 오류는 아래 bootJar가 잡는다.
# 단, 원인 추적을 위해 출력은 숨기지 않는다(이전 `> /dev/null 2>&1` 제거).
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true

# 2) 소스 복사 후 bootJar.
#    'bootJar'만 실행하므로 plain jar('jar' 태스크)는 생성되지 않아 build/libs에 실행 가능 jar 하나만 남는다.
#    테스트는 CI(S2)가 책임지므로 이미지 빌드에서는 제외(-x test).
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# ===== Stage 2: runtime =====
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# non-root 실행 — 컨테이너 침해 시 권한 최소화
RUN addgroup -S app && adduser -S app -G app

COPY --from=builder /build/build/libs/*.jar /app/app.jar
RUN chown -R app:app /app
USER app

# 컨테이너 메모리 인지 JVM 힙 상한(cgroup 인식) + OOM 시 즉시 종료(blue/green 헬스 게이트가 감지해 cutover 중단).
# JDK_JAVA_OPTIONS는 java가 자동 인식 → exec-form ENTRYPOINT를 유지하면서 런타임에 -e JDK_JAVA_OPTIONS로 오버라이드 가능.
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Seoul"

# 운영 배포 아티팩트라 prod 프로파일을 이미지에 고정한다. 미지정 시 application.yml 기본값(local)으로 떠
# actuator가 8080에 붙어 아래 9090 HEALTHCHECK가 영구 unhealthy가 된다. 로컬 실행 시엔 -e SPRING_PROFILES_ACTIVE로 오버라이드.
ENV SPRING_PROFILES_ACTIVE=prod

# 8080=앱 트래픽, 9090=actuator(management.server.port, ALB health check). EXPOSE는 문서용 — task-definition portMappings와 일치.
EXPOSE 8080 9090

# HEALTHCHECK: 운영 actuator는 prod 프로파일에서 main(8080)과 분리된 별도 포트 9090에 뜬다(application-prod.yml management.server.port=9090). 9090을 본다.
#   - 컨테이너 내부 loopback 호출이라, ECS 전환(E4)으로 바인딩이 0.0.0.0이 되어도(외부 노출은 SG로 차단) localhost로 도달 가능.
#   - alpine은 curl 미포함 → busybox wget 사용.
#   - 위 ENV SPRING_PROFILES_ACTIVE=prod로 9090 전제를 이미지가 자체 보장한다(로컬 실행 시엔 프로파일 교체 필요).
#   - start-period 90s: 콜드 스타트 + 부팅 시 Flyway migrate 누적 감안한 여유(실운영 실측 후 조정).
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --retries=3 \
  CMD wget -q -O - http://127.0.0.1:9090/actuator/health || exit 1

# exec-form: SIGTERM이 셸을 거치지 않고 PID1(JVM)에 직접 전달 → graceful shutdown(S0)이 실제로 작동.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
