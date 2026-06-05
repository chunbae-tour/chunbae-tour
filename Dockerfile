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
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

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

EXPOSE 8080

# HEALTHCHECK: 운영 actuator는 prod 프로파일에서 9090/127.0.0.1로 격리되므로(application-prod.yml) 9090을 본다.
#   - 컨테이너 내부 loopback 호출이라 9090이 외부 미노출(loopback bind)이어도 도달 가능.
#   - alpine은 curl 미포함 → busybox wget 사용.
#   - ⚠️ 이 이미지는 prod 프로파일 배포 전제(SPRING_PROFILES_ACTIVE=prod). 로컬(local 프로파일, actuator 8080)
#     로 그냥 돌리면 9090에 health가 없어 unhealthy로 뜬다 — 배포용 아티팩트라 의도된 동작.
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -q -O - http://127.0.0.1:9090/actuator/health || exit 1

# exec-form: SIGTERM이 셸을 거치지 않고 PID1(JVM)에 직접 전달 → graceful shutdown(S0)이 실제로 작동.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
