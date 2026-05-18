# 춘배투어 (chunbae-tour)

Spring Boot 기반 여행 서비스 프로젝트입니다.

<!-- Copilot review test -->


## 기술 스택

- Java 21
- Spring Boot 4.0.6
- Spring Framework 7
- Gradle 8.14+
- MySQL 8.4
- Redis 7

## 로컬 개발 환경 실행

자세한 사용법과 문제 해결 방법은 [로컬 개발 환경 사용 가이드](./docs/local-development.md)를 참고하세요.

### 사전 요구사항

- Java 21
- Docker Desktop

### 1. 환경 변수 파일 생성

macOS/Linux:

```bash
cp .env.example .env
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

`.env` 기본값:

```dotenv
DB_HOST=localhost
DB_PORT=3307
DB_NAME=chunbae_tour
DB_USERNAME=chunbae
DB_PASSWORD=1234

REDIS_HOST=localhost
REDIS_PORT=6380
REDIS_PASSWORD=
```

### 2. Docker 컨테이너 실행

```bash
docker compose up -d
docker compose ps
```

또는 스크립트를 사용할 수 있습니다.

macOS/Linux:

```bash
sh scripts/dev-up.sh
```

Windows PowerShell:

```powershell
.\scripts\dev-up.ps1
```

### 3. 애플리케이션 실행

macOS/Linux:

```bash
./gradlew bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

`local` 프로필은 `.env` 파일을 선택적으로 읽고, 값이 없으면 기본값을 사용합니다.

### 포트 충돌 해결

MySQL 기본 로컬 포트는 `3307`, Redis 기본 로컬 포트는 `6380`입니다.

이미 사용 중인 포트가 있다면 `.env`에서 값을 변경한 뒤 컨테이너를 다시 실행하세요.

```dotenv
DB_PORT=3308
REDIS_PORT=6381
```

```bash
docker compose up -d
```

### 종료

```bash
docker compose down
```

또는 스크립트를 사용할 수 있습니다.

macOS/Linux:

```bash
sh scripts/dev-down.sh
```

Windows PowerShell:

```powershell
.\scripts\dev-down.ps1
```

### 데이터 초기화

MySQL/Redis 볼륨까지 삭제하려면 다음 명령을 실행합니다.

```bash
docker compose down -v
```

## 라이선스

이 프로젝트는 MIT License를 따릅니다. 자세한 내용은 [LICENSE](./LICENSE)를 참고하세요.
