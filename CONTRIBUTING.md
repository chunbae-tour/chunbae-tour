# 기여 가이드

## 브랜치 전략
- `main`: 배포 전용 (직접 push 금지)
- `develop`: 개발 통합 브랜치 (모든 feature가 모이는 곳)
- `feature/이슈번호-설명`: 기능 개발 (예: `feature/12-add-login`)
- `fix/이슈번호-설명`: 버그 수정 (예: `fix/18-fix-signup`)
- `hotfix/설명`: 긴급 운영 수정 (main에서 직접 분기)

## 작업 시작 방법
```bash
# 1. develop 최신화
git checkout develop
git pull origin develop

# 2. 이슈 번호 확인 후 브랜치 생성
git checkout -b feature/이슈번호-설명

# 3. 작업 완료 후 PR 생성 (base: develop)
gh pr create --base develop
```

## 커밋 메시지 컨벤션
```
feat: 새로운 기능 추가
fix: 버그 수정
chore: 빌드/설정 변경 (기능 변경 없음)
docs: 문서 수정
refactor: 코드 리팩토링
test: 테스트 추가/수정
style: 코드 포맷팅
```

## PR 규칙
- PR 제목: `feat: 로그인 기능 추가` 형식 준수
- base 브랜치: 반드시 `develop`
- Squash and merge 사용
- develop → main 배포 PR만 Merge commit 사용
