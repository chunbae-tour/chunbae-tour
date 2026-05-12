# 춘배투어 (chunbae-tour)

> 팀 프로젝트

## 📌 프로젝트 소개
(TBD - PR #2에서 작성)

## 🛠 기술 스택
- Java 21
- Spring Boot 4.0.6
- Spring Framework 7
- Gradle 8.14+ (Groovy DSL)
- (DB, Redis 등 TBD - PR #2 이후 확정)

## 🚀 시작하기

### 사전 요구사항
- Java 21
- Docker Desktop

### 로컬 실행
```bash
# 1. 환경변수 파일 생성
cp .env.example .env

# 2. Docker 컨테이너 실행 (MySQL + Redis)
docker compose up -d

# 3. 애플리케이션 실행
./gradlew bootRun
```

### 컨테이너 포트
| 서비스 | 로컬 포트 |
|---|---|
| MySQL | 3307 |
| Redis | 6380 |

## 👥 팀원
(TBD)

## 📜 라이선스
이 프로젝트는 MIT License를 따릅니다. 자세한 내용은 [LICENSE](./LICENSE) 파일을 참고하세요.
