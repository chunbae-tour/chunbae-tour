# KAN-23 S3 후속 작업 목록

S3 리뷰에서 지금 코드로 확정할 수 없거나 운영 정보가 필요한 항목을 분리한다.

작성 기준: 2026-05-19, `feature/KAN-23-s3-token-rotation`

---

## 1. 운영 도메인 / Refresh Cookie SameSite 정책 확인

- **현재 상태**: Refresh Cookie는 `SameSite=Lax`, 운영은 `Secure=true`, CORS 허용 origin은 환경변수로 주입한다.
- **보류 이유**: 운영 프론트엔드와 API가 같은 site인지, 서로 다른 site인지 코드만으로 확정할 수 없다.
- **리스크**: 서로 다른 site 배치라면 브라우저가 `POST /api/v1/auth/reissue` 요청에 Refresh Cookie를 보내지 않을 수 있다. 이 경우 `SameSite=None; Secure` 전환이 필요할 수 있다.
- **결정 필요**:
  - 운영 프론트엔드 도메인
  - 운영 API 도메인
  - `SameSite=Lax` 유지 또는 `SameSite=None; Secure` 전환
  - 운영 `CORS_ALLOWED_ORIGINS` 값
- **검증 기준**: 운영 또는 운영과 동일한 도메인 구성에서 로그인 후 `Set-Cookie`가 저장되고, 재발급 요청에 Refresh Cookie가 실제로 포함되는지 브라우저 네트워크 탭으로 확인한다.
