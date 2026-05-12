# Agent Harness 문서 구조

이 디렉터리는 AI 코딩 에이전트가 작업 전에 필요한 규칙만 빠르게 읽기 위한 하네스 문서 모음이다.

## 진입점

- 루트 `AGENTS.md`: 에이전트가 가장 먼저 읽는 파일
- `00-READING-MAP.md`: 작업 유형별로 추가로 읽을 문서를 정하는 파일
- `01-AGENT-HARNESS.md`: 모든 작업에서 반드시 적용하는 안전 규칙

## 파일 배치 규칙

- `00-*`: 읽기 순서와 라우팅
- `01-*`: 모든 작업에 적용되는 공통 하네스
- `02-*` ~ `08-*`: 작업 유형별 세부 규칙
- `09-*` 이후: 새 작업 유형이 생길 때 추가
- `99-*`: 완료된 기능, 운영 메모, 변경 이력처럼 낮은 우선순위 참고 자료

새 문서를 추가하면 반드시 `00-READING-MAP.md`에 어떤 작업에서 읽어야 하는지 연결한다.

## 현재 문서

| 파일 | 역할 |
|---|---|
| `00-READING-MAP.md` | 작업 유형별 읽기 지도 |
| `01-AGENT-HARNESS.md` | 모든 에이전트 공통 안전 규칙 |
| `02-PROJECT-CONTEXT.md` | 프로젝트 기본 정보와 로컬 개발 환경 |
| `03-GIT-WORKFLOW.md` | 브랜치, 커밋, PR 규칙 |
| `04-CODING-RULES.md` | Spring Boot와 일반 코딩 규칙 |
| `05-DOMAIN-DESIGN.md` | 도메인, Entity, DB 작업 규칙 |
| `06-TASK-CHECKLISTS.md` | 작업 유형별 체크리스트 |
| `07-OPERATIONS.md` | Docker, CI/CD, 운영성 작업 규칙 |
| `08-TROUBLESHOOTING.md` | 반복 문제와 해결 기록 |
| `99-DONE-FEATURES.md` | 완료된 기능 기록 |
