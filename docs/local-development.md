# 로컬 개발 환경 사용 가이드

이 문서는 팀원이 Windows, macOS, Linux에서 Docker 기반 로컬 개발 환경을 같은 방식으로 실행할 수 있도록 정리한 가이드입니다.

## 이 설정이 해주는 일

- MySQL 8.4 컨테이너를 실행합니다.
- Redis 7 컨테이너를 실행합니다.
- MySQL/Redis 포트와 DB 계정 정보를 `.env`에서 관리합니다.
- 팀원마다 로컬 포트가 다를 때 `.env`만 수정해서 충돌을 피할 수 있습니다.
- Spring Boot `local` 프로필이 `.env` 값을 읽어 Docker Compose 설정과 같은 DB/Redis에 접속합니다.
- `scripts/dev-up.*`, `scripts/dev-down.*` 스크립트로 OS별 실행 명령 차이를 줄입니다.

## 처음 한 번만 준비할 것

1. Java 21을 설치합니다.
2. Docker Desktop을 설치하고 실행합니다.
3. 프로젝트 루트에서 `.env` 파일을 만듭니다.

macOS/Linux:

```bash
cp .env.example .env
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

## 기본 `.env` 값

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

보통은 이 값 그대로 사용하면 됩니다.

## 실행 방법

### 방법 1. 스크립트로 실행

macOS/Linux:

```bash
sh scripts/dev-up.sh
```

Windows PowerShell:

```powershell
.\scripts\dev-up.ps1
```

스크립트는 `.env`가 없으면 자동으로 `.env.example`을 복사한 뒤 `docker compose up -d`를 실행합니다.

### 방법 2. Docker Compose 명령으로 실행

```bash
docker compose up -d
docker compose ps
```

`docker compose ps`에서 `chunbae-tour-mysql`, `chunbae-tour-redis`가 `Up` 또는 `healthy` 상태면 정상입니다.

## 애플리케이션 실행

Docker 컨테이너가 실행된 뒤 Spring Boot 애플리케이션을 실행합니다.

macOS/Linux:

```bash
./gradlew bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

## 접속 정보

기본 설정 기준입니다.

| 항목 | 값 |
|---|---|
| MySQL Host | `localhost` |
| MySQL Port | `3307` |
| MySQL Database | `chunbae_tour` |
| MySQL Username | `chunbae` |
| MySQL Password | `1234` |
| Redis Host | `localhost` |
| Redis Port | `6380` |
| Redis Password | 빈 값 |

## 포트 충돌 해결

로컬 PC에서 이미 MySQL `3307` 포트를 사용 중이면 Docker 실행이 실패할 수 있습니다.

이 경우 `.env`에서 `DB_PORT`만 다른 값으로 바꿉니다.

```dotenv
DB_PORT=3308
```

Redis 포트가 충돌하면 `REDIS_PORT`를 바꿉니다.

```dotenv
REDIS_PORT=6381
```

변경 후 다시 실행합니다.

```bash
docker compose up -d
docker compose ps
```

Spring Boot `local` 프로필도 같은 `.env` 값을 읽기 때문에, `application-local.yml`을 직접 수정하지 않아도 됩니다.

## 종료 방법

### 컨테이너만 종료

macOS/Linux:

```bash
sh scripts/dev-down.sh
```

Windows PowerShell:

```powershell
.\scripts\dev-down.ps1
```

또는 직접 실행합니다.

```bash
docker compose down
```

### 데이터를 포함해서 초기화

MySQL/Redis 데이터를 모두 지우고 처음 상태로 다시 시작하려면 볼륨까지 삭제합니다.

```bash
docker compose down -v
```

이 명령은 로컬 개발 DB 데이터를 삭제하므로 필요한 데이터가 있으면 먼저 백업하세요.

## 자주 겪는 문제

### `port is already allocated`

이미 다른 프로그램이 같은 포트를 사용 중입니다.

- MySQL이면 `.env`의 `DB_PORT`를 `3308` 같은 다른 값으로 변경합니다.
- Redis이면 `.env`의 `REDIS_PORT`를 `6381` 같은 다른 값으로 변경합니다.
- 변경 후 `docker compose up -d`를 다시 실행합니다.

### `Access denied for user`

기존 MySQL 볼륨에 이전 계정 정보가 남아 있을 수 있습니다.

로컬 데이터가 필요 없다면 다음 명령으로 볼륨을 초기화합니다.

```bash
docker compose down -v
docker compose up -d
```

### Docker 명령이 실행되지 않음

- Docker Desktop이 실행 중인지 확인합니다.
- Windows에서는 PowerShell을 새로 열어 다시 시도합니다.
- Docker Desktop 설치 직후라면 PC 재부팅이 필요할 수 있습니다.

## 설정 파일 역할

| 파일 | 역할 |
|---|---|
| `.env.example` | 팀원이 복사해서 사용할 환경변수 예시 |
| `.env` | 개인 로컬 환경변수 파일, Git에 커밋하지 않음 |
| `docker-compose.yml` | MySQL/Redis 컨테이너 실행 설정 |
| `src/main/resources/application-local.yml` | Spring Boot local 프로필 DB/Redis 접속 설정 |
| `scripts/dev-up.sh` | macOS/Linux 컨테이너 실행 스크립트 |
| `scripts/dev-up.ps1` | Windows PowerShell 컨테이너 실행 스크립트 |
| `scripts/dev-down.sh` | macOS/Linux 컨테이너 종료 스크립트 |
| `scripts/dev-down.ps1` | Windows PowerShell 컨테이너 종료 스크립트 |
