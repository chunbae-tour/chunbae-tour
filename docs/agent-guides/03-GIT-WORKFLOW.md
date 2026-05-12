# 03-GIT-WORKFLOW.md

## 브랜치 전략

- `main`: 배포 또는 릴리즈 전용
- `develop`: 개발 통합 브랜치
- `feature/*`: 기능 개발
- `fix/*`: 버그 수정
- `chore/*`: 설정, 문서, 협업 도구
- `hotfix/*`: 긴급 수정

## 기본 규칙

- 모든 기능 PR의 base는 `develop`이다.
- `main`에 직접 push하지 않는다.
- PR은 생성만 하고 merge는 사용자가 한다.
- 작업 시작 전 기준 브랜치를 최신화한다.
- 사용자 승인 없이 force push하지 않는다.
- 사용자 승인 없이 squash, rebase, reset을 하지 않는다.

## 작업 시작 절차

1. 현재 브랜치를 확인한다.
2. 변경사항이 있는지 확인한다.
3. 기준 브랜치가 필요한 경우 최신화한다.
4. 작업 브랜치를 만든다.

예시:

- `git status --short --branch`
- `git checkout develop`
- `git pull origin develop`
- `git checkout -b feature/이슈번호-설명`

## 커밋 메시지

권장 prefix:

- `feat`: 새로운 기능
- `fix`: 버그 수정
- `chore`: 빌드, 설정, 의존성
- `docs`: 문서
- `refactor`: 리팩토링
- `test`: 테스트
- `style`: 포맷, 스타일

## PR 생성 전 확인

- 변경 파일 목록을 확인한다.
- 보호 파일 변경 여부를 확인한다.
- secret 포함 여부를 확인한다.
- 빌드 또는 테스트를 실행한다.
- PR base가 `develop`인지 확인한다.
- release PR만 `main`을 base로 사용할 수 있다.

## 금지

- `main` 직접 push
- PR 자동 merge
- 사용자 승인 없는 force push
- 사용자 승인 없는 reset
- 사용자 승인 없는 protected branch 설정 변경
