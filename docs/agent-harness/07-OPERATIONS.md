# 07-OPERATIONS.md

이 문서는 CI/CD, 배포, 운영 환경 작업에서 읽는다.

## 현재 상태

현재 프로젝트는 초기 세팅 단계다.

확인된 구성:

- GitHub Actions CI
- Docker Compose 로컬 개발 환경
- MySQL
- Redis

운영 배포 방식은 확정 후 이 문서에 기록한다.

## CI/CD 원칙

- CI 실패 상태에서 PR을 merge하지 않는다.
- 배포 방식 변경은 사용자 승인 후 진행한다.
- secret은 GitHub Actions secret 또는 안전한 secret 저장소를 사용한다.
- 로그에 secret이 노출되지 않도록 한다.

## 로컬 운영 명령

Docker 실행:

- `docker compose up -d`

Docker 상태 확인:

- `docker compose ps`

Docker 종료:

- `docker compose down`

데이터까지 초기화:

- `docker compose down -v`

주의:

- `docker compose down -v`는 로컬 DB 데이터를 삭제한다.
- 팀원에게 안내 없이 기본 포트를 바꾸지 않는다.

## 장애 기록 방식

운영 또는 로컬 개발 환경에서 반복 가능한 문제가 생기면
`docs/agent-harness/08-TROUBLESHOOTING.md`에 기록한다.

기록 형식:

- 날짜
- 증상
- 원인
- 해결 방법
- 재발 방지
