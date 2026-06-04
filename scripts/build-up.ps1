# ============================================================
# build-up.ps1 — 로컬 개발 환경 전체 빌드 & 기동 스크립트 (Windows PowerShell)
#
# 【최초 1회 설정 방법】
#   1. PowerShell을 관리자 권한으로 열기
#   2. 스크립트 실행 정책 허용:
#      Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
#   3. .env 파일 준비:
#      Copy-Item .env.example .env  → 메모장 등으로 열어 값 채우기
#
# 【실행 방법】
#   PowerShell에서 프로젝트 루트로 이동 후:
#   .\scripts\build-up.ps1
#
# 【종료 방법】
#   로그 스트림:  Ctrl+C  (컨테이너는 계속 실행됨)
#   컨테이너 중지: .\scripts\dev-down.ps1
# ============================================================

$ErrorActionPreference = "Stop"  # 명령 실패 시 즉시 종료 (sh의 set -e 와 동일)

# 스크립트 위치 기준으로 프로젝트 루트 이동 (어느 경로에서 실행해도 동작)
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Resolve-Path (Join-Path $ScriptDir "..")
Set-Location $RootDir

# ── STEP 1: Gradle로 실행 가능한 JAR 파일 빌드 ──────────────────────────────
# bootJar: 의존성이 포함된 단독 실행 JAR(fat jar)를 build/libs/ 에 생성
# Dockerfile이 이 JAR을 컨테이너 안으로 복사해 사용하므로 먼저 빌드 필요
Write-Host "=== [1/2] Gradle bootJar 빌드 시작 ===" -ForegroundColor Cyan
.\gradlew.bat bootJar

# ── STEP 2: Docker Compose로 전체 스택 기동 ─────────────────────────────────
# --build: STEP 1에서 만든 JAR을 포함해 Docker 이미지를 새로 빌드
# -d (detached): 백그라운드 실행 — 터미널을 점유하지 않음
# 기동 대상: docker-compose.yml에 정의된 서비스 전체 (app, db, redis 등)
Write-Host "=== [2/2] Docker Compose 풀스택 기동 ===" -ForegroundColor Cyan
docker compose up -d --build

# ── STEP 3: 앱 로그 실시간 출력 ─────────────────────────────────────────────
# -f (follow): 로그를 스트리밍으로 출력 (tail -f 와 동일)
# app: docker-compose.yml 의 서비스 이름 — Spring Boot 앱 컨테이너만 필터링
# Ctrl+C로 로그 출력만 중단, 컨테이너는 백그라운드에서 계속 실행됨
Write-Host "=== 기동 완료 — 앱 로그 출력 시작 (중지: Ctrl+C) ===" -ForegroundColor Green
docker compose logs -f app
