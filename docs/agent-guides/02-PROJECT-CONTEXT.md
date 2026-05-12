# 02-PROJECT-CONTEXT.md

## 프로젝트 기본 정보

| 항목 | 값 |
| --- | --- |
| 프로젝트명 | `chunbae-tour` |
| 패키지명 | `com.chunbaetour` |
| 빌드 도구 | Gradle |
| Java | 21 |
| Spring Boot | 4.0.6 |
| 주요 인프라 | MySQL, Redis |
| 기준 브랜치 | `develop` |

## 로컬 개발 환경

필수 도구:

- Java 21
- Docker Desktop
- Git

기본 실행 순서:

1. `.env.example`을 `.env`로 복사한다.
2. Docker Compose로 MySQL과 Redis를 실행한다.
3. Spring Boot 애플리케이션을 실행한다.

주요 명령:

- `docker compose up -d`
- `docker compose ps`
- `./gradlew bootRun`
- `docker compose down`

Windows PowerShell에서는 Gradle 실행 시 `.\gradlew.bat`을 사용할 수 있다.

## Docker 기본 포트

| 서비스 | 기본 포트 |
| --- | --- |
| MySQL | `3307` |
| Redis | `6380` |
| Spring Boot | `8080` |

포트 충돌 시 `.env`에서 로컬 포트를 변경한다.

예:

- `DB_PORT=3308`
- `REDIS_PORT=6381`

## 환경변수 원칙

- 팀 공통 기본값은 `.env.example`에 둔다.
- 개인 로컬 값은 `.env`에 둔다.
- `.env`는 커밋하지 않는다.
- 앱 설정과 Docker Compose의 포트 값은 같은 환경변수를 사용한다.
