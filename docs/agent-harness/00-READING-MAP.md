# 00-READING-MAP.md

이 문서는 작업 유형별로 에이전트가 읽어야 할 문서를 정한다.
토큰 절약을 위해 관련 없는 문서는 읽지 않는다.

## 항상 읽는 문서

모든 작업에서 다음 문서는 반드시 읽는다.

- `AGENTS.md`
- `docs/agent-harness/01-AGENT-HARNESS.md`

## Git, PR, 브랜치 작업

읽을 문서:

- `docs/agent-harness/03-GIT-WORKFLOW.md`

예시:

- 브랜치 생성
- 커밋
- PR 생성
- PR 상태 확인
- 릴리즈 PR 준비

## 일반 버그 수정

읽을 문서:

- `docs/agent-harness/04-CODING-RULES.md`
- `docs/agent-harness/06-TASK-CHECKLISTS.md`

DB, Entity, 핵심 도메인과 관련 있으면 추가로 읽는다.

- `docs/agent-harness/05-DOMAIN-DESIGN.md`

## 신규 기능 개발

읽을 문서:

- `docs/agent-harness/02-PROJECT-CONTEXT.md`
- `docs/agent-harness/04-CODING-RULES.md`
- `docs/agent-harness/05-DOMAIN-DESIGN.md`
- `docs/agent-harness/06-TASK-CHECKLISTS.md`
- `docs/agent-harness/03-GIT-WORKFLOW.md`

## DB, Entity, Repository 작업

읽을 문서:

- `docs/agent-harness/05-DOMAIN-DESIGN.md`
- `docs/agent-harness/04-CODING-RULES.md`
- `docs/agent-harness/06-TASK-CHECKLISTS.md`

주의:

- DB 스키마 변경은 사용자 승인 없이 진행하지 않는다.

## Docker, 로컬 개발 환경 작업

읽을 문서:

- `docs/agent-harness/02-PROJECT-CONTEXT.md`
- `docs/agent-harness/06-TASK-CHECKLISTS.md`

필요할 때만 추가로 읽는다.

- `docs/agent-harness/07-OPERATIONS.md`

## 보안 작업

읽을 문서:

- `docs/agent-harness/01-AGENT-HARNESS.md`
- `docs/agent-harness/04-CODING-RULES.md`
- `docs/agent-harness/06-TASK-CHECKLISTS.md`

화면, 템플릿, 요청 처리, 인증, 권한과 관련 있으면
CSRF, XSS, 인증, 인가 체크리스트를 반드시 확인한다.

## CI/CD, 배포 작업

읽을 문서:

- `docs/agent-harness/03-GIT-WORKFLOW.md`
- `docs/agent-harness/07-OPERATIONS.md`
- `docs/agent-harness/08-TROUBLESHOOTING.md`

## 단순 질문, 코드 설명

읽을 문서:

- `AGENTS.md`
- 필요한 소스 파일

단순 설명 작업에서는 전체 하네스 문서를 불필요하게 읽지 않는다.
