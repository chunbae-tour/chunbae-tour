# ChunBae Tour — Frontend Design Specification
**문서 버전**: v2.0  
**기준일**: 2026-05-22  
**프로젝트명**: 춘배투어 (ChunBae Tour)  
**대상 AI**: 프론트엔드 구현 에이전트 (React + PWA)

> **이 문서의 목적**: AI 에이전트가 UI를 구현할 때 발생하는 설계 해석의 모호함을 제거한다.  
> 모든 수치·색상·동작은 이 문서를 최우선 기준으로 삼는다. 문서에 명시되지 않은 경우에만 자체 판단을 허용한다.

---

## 0. 설계 철학 (AI가 판단 기준으로 사용할 원칙)

| 원칙 | 설명 |
|------|------|
| **컬러 절제** | 다중 도메인(지도, 캘린더, 스토어, 채팅)이 공존하므로 Primary 컬러는 CTA·활성 상태·마커에만 한정 사용 |
| **포용적 설계** | 전통시장 상인 및 고령층을 위한 최소 텍스트 크기 14px, 명확한 아이콘+레이블 병용 |
| **기능 중심** | 장식적 애니메이션보다 상태 전환을 명확히 알리는 피드백 애니메이션 우선 |
| **엽전 아이덴티티** | Secondary(골드) 컬러는 엽전·뱃지·인증 마크 전용. 다른 UI에 혼용 금지 |

---

## 1. 디자인 토큰 (Design Tokens)

### 1.1 색상 시스템

```css
/* === Primary === */
--color-primary:        #FF6B35;   /* 선셋 오렌지 — CTA 버튼, 활성 탭, 지도 마커 */
--color-primary-light:  #FF8C5A;   /* 호버 상태 */
--color-primary-dark:   #E5561F;   /* 눌림(Active) 상태 */
--color-primary-subtle: #FFF0EA;   /* 배경 강조 영역, 선택된 리스트 항목 배경 */

/* === Secondary (엽전 전용) === */
--color-secondary:      #FFEA36;   /* 엽전 골드 — 엽전 UI, 뱃지, 상인 인증 마크 전용 */
--color-secondary-dark: #D4BC00;   /* 엽전 아이콘 테두리·그림자 */

/* === Neutral === */
--color-bg-base:        #F8F9FA;   /* 전체 페이지 배경 */
--color-bg-card:        #FFFFFF;   /* 카드, 모달, 드로어 배경 */
--color-bg-overlay:     rgba(0, 0, 0, 0.45); /* 모달 딤 처리 */

--color-border:         #E5E7EB;   /* 카드·입력 필드 기본 테두리 */
--color-border-focus:   #FF6B35;   /* 포커스 링 */

/* === Text === */
--color-text-primary:   #111827;   /* 제목, 중요 정보 (원문 #333333에서 대비 강화) */
--color-text-secondary: #4B5563;   /* 본문 */
--color-text-muted:     #9CA3AF;   /* 보조 설명, 플레이스홀더 */
--color-text-disabled:  #D1D5DB;

/* === Semantic === */
--color-success:        #22C55E;
--color-warning:        #F59E0B;
--color-error:          #EF4444;
--color-info:           #3B82F6;
```

> **수정 이유**: 원문 본문 색상 `#333333`을 `#111827`로, 보조 텍스트를 `#767676`에서 `#4B5563`으로 조정.  
> WCAG AA 기준(4.5:1 이상) 충족을 위한 대비 강화 조치. 고령층 가독성 요건과도 직결됨.

---

### 1.2 타이포그래피

```css
/* === Font Stack === */
--font-primary: 'Pretendard', 'Apple SD Gothic Neo', 'Noto Sans KR', sans-serif;
--font-display: 'Noto Serif KR', 'Georgia', serif;  /* 축제·랜딩 섹션 타이틀 전용 */
--font-mono:    'JetBrains Mono', 'Consolas', monospace; /* 가격, 수량 등 숫자 */
```

```css
/* === 타입 스케일 === */
--text-xs:   12px;  /* line-height: 1.5 — 태그, 뱃지 */
--text-sm:   14px;  /* line-height: 1.6 — 보조 설명, 캡션 */
--text-base: 16px;  /* line-height: 1.7 — 본문 기본값 */
--text-lg:   18px;  /* line-height: 1.6 — 섹션 소제목 */
--text-xl:   20px;  /* line-height: 1.5 — 카드 제목 */
--text-2xl:  24px;  /* line-height: 1.4 — 페이지 소제목 */
--text-3xl:  30px;  /* line-height: 1.3 — 페이지 메인 타이틀 */
--text-4xl:  36px;  /* line-height: 1.2 — 랜딩 히어로 */

/* === Font Weight === */
--fw-regular: 400;
--fw-medium:  500;
--fw-bold:    700;
```

> **최소 텍스트 규칙**: 어떠한 UI 요소도 `--text-sm (14px)` 미만을 사용하지 않는다.  
> 가격 정보는 `--font-mono` + `--fw-bold` 조합으로 통일하여 숫자 가독성을 높인다.

---

### 1.3 스페이싱 시스템 (4px 기반 8배수 격자)

```css
--space-1:  4px;
--space-2:  8px;
--space-3:  12px;
--space-4:  16px;
--space-5:  20px;
--space-6:  24px;
--space-8:  32px;
--space-10: 40px;
--space-12: 48px;
--space-16: 64px;
```

> 컴포넌트 내부 패딩, 마진, 갭은 반드시 이 값 중 하나를 사용한다.

---

### 1.4 모서리 반경 (Border Radius)

```css
--radius-sm:   4px;   /* 태그, 배지, 퀵 필터 칩 */
--radius-md:   8px;   /* 버튼, 입력 필드 */
--radius-lg:   12px;  /* 카드 */
--radius-xl:   16px;  /* 모달, 바텀 시트 */
--radius-full: 9999px; /* 아바타, 원형 버튼 */
```

---

### 1.5 그림자 (Box Shadow)

```css
--shadow-sm:  0 1px 3px rgba(0,0,0,0.08);          /* 카드 기본 */
--shadow-md:  0 4px 12px rgba(0,0,0,0.10);          /* 카드 호버, 드롭다운 */
--shadow-lg:  0 8px 24px rgba(0,0,0,0.12);          /* 모달, 바텀 시트 */
--shadow-map: 0 2px 8px rgba(255,107,53,0.35);       /* 지도 마커 강조 */
```

---

### 1.6 Z-Index 레이어

```css
--z-base:       0;
--z-card:       10;
--z-dropdown:   100;
--z-sticky:     200;    /* 헤더, 사이드바 */
--z-overlay:    300;    /* 지도 오버레이, 바텀 시트 */
--z-modal:      400;
--z-toast:      500;
--z-tooltip:    600;
```

---

### 1.7 트랜지션 (Transition)

```css
--transition-fast:   150ms ease;          /* 버튼 hover, 색상 변화 */
--transition-base:   250ms ease;          /* 카드 hover, 상태 전환 */
--transition-slow:   400ms ease-in-out;   /* 슬라이드 다운, 바텀 시트 */
--transition-spring: 350ms cubic-bezier(0.34, 1.56, 0.64, 1); /* 마커 팝업 */
```

---

## 2. 레이아웃 시스템

### 2.1 그리드 컨테이너

```css
.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 var(--space-6);  /* 좌우 24px */
}

/* 12컬럼 그리드 */
.grid {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: var(--space-6);  /* 24px */
}
```

---

### 2.2 헤더 (Global Navigation Bar)

```
┌─────────────────────────────────────────────────────────────────┐
│ ◈ 춘배투어  │  홈   지도   축제   스토어   커뮤니티    🗨  [😊] │
│  ← 24px →  │← 60px gap →  ← 40px 간격 →         ← 24px gaps → │
└─────────────────────────────────────────────────────────────────┘
```

| 속성 | 값 |
|------|----|
| 높이 | `72px` |
| 배경 | `#FFFFFF` + `--shadow-sm` |
| 포지션 | `position: fixed; top: 0; width: 100%; z-index: var(--z-sticky)` |
| 로고 좌측 여백 | `24px` |
| GNB 메뉴 시작 | 로고 우측 `60px` |
| GNB 메뉴 간격 | `40px` |
| 우측 영역 아이콘 간격 | `24px` |
| 프로필 썸네일 | `36px × 36px`, `border-radius: var(--radius-full)` |
| GNB 활성 항목 | `color: var(--color-primary)`, 하단 `2px solid var(--color-primary)` 언더라인 |
| 헤더 하단 여백 보상 | `body { padding-top: 72px }` |

---

### 2.3 3단 레이아웃 (기본 — 커뮤니티·스토어·피드)

```
┌──────────┬──────────────────────────────┬────────────┐
│  LNB     │      메인 콘텐츠              │  우측바    │
│  220px   │         680px                │   252px    │
│          │                              │            │
└──────────┴──────────────────────────────┴────────────┘
              ↑ gutter 24px ↑                ↑ 24px ↑
```

| 영역 | 너비 | 역할 |
|------|------|------|
| 좌측 사이드바 (LNB) | `220px` | 카테고리 목록, 게시판 분류 |
| 메인 콘텐츠 | `680px` | 상품 그리드, 게시글 리스트, 캘린더 |
| 우측 사이드바 | `252px` | 인기 검색어, 퀵 배너 |

> `220 + 24 + 680 + 24 + 252 = 1200px` — 컨테이너 최대 폭과 정확히 일치.

---

## 3. 도메인별 특화 레이아웃

### 3.1 지도 & 관광지 탐색 (Split Layout)

```
┌──────────────────────────────────────────────────────────────────┐
│ HEADER (72px, fixed)                                             │
├──────────────┬───────────────────────────────────────────────────┤
│  검색/결과   │                                                   │
│  380px       │          카카오맵 (나머지 전체 폭)                │
│  overflow-y  │                                                   │
│  scroll      │                                                   │
│              │                                                   │
└──────────────┴───────────────────────────────────────────────────┘
```

```css
.map-layout {
  position: fixed;
  top: 72px;          /* 헤더 높이만큼 오프셋 */
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
}

.map-panel-left {
  width: 380px;
  min-width: 320px;
  overflow-y: auto;
  background: var(--color-bg-card);
  box-shadow: var(--shadow-md);
  z-index: var(--z-overlay);
}

.map-panel-right {
  flex: 1;
  position: relative;  /* 카카오맵 컨테이너 */
}
```

**인터랙션 규칙**
- 지도 마커 클릭 → 좌측 패널 해당 항목 스크롤 이동 + `border: 2px solid var(--color-primary)` 하이라이트
- 좌측 항목 클릭 → 지도 해당 좌표 중앙 이동 + 마커 `--shadow-map` 강조
- 팝업 오버레이 금지: 지도 위 복잡한 인포윈도우 사용 금지. 모든 상세 정보는 좌측 패널에 표시.

**퀵 필터 칩 (Quick Filter Chips)**
```css
.chip {
  height: 32px;
  padding: 0 var(--space-3);
  border-radius: var(--radius-full);
  border: 1px solid var(--color-border);
  font-size: var(--text-sm);
  background: var(--color-bg-card);
  cursor: pointer;
  transition: var(--transition-fast);
  white-space: nowrap;
}
.chip.active {
  background: var(--color-primary);
  color: #FFFFFF;
  border-color: var(--color-primary);
}
```
> 예시 칩: `#주차가능` `#야시장` `#무료입장` `#반려동물` `#포토존`

---

### 3.2 축제 캘린더 (Wide Grid)

```css
.calendar-wrapper {
  width: 1000px;
  margin: var(--space-8) auto;
  /* 좌·우측 사이드바 숨김 처리 */
}

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 1px;
  background: var(--color-border);  /* 셀 구분선 역할 */
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.calendar-cell {
  min-height: 120px;      /* 원문 120px 유지 */
  min-width: 130px;       /* 원문 130px 유지 */
  padding: var(--space-2);
  background: var(--color-bg-card);
  cursor: pointer;
  transition: background var(--transition-fast);
}
.calendar-cell:hover {
  background: var(--color-primary-subtle);
}
.calendar-cell.has-festival {
  border-top: 3px solid var(--color-primary);
}
```

**슬라이드 다운 상세 패널**
```css
.festival-detail-panel {
  height: 0;
  overflow: hidden;
  transition: height var(--transition-slow);
  /* JS로 grid-column: 1 / -1 설정하여 해당 주(row) 전체 폭 차지 */
}
.festival-detail-panel.open {
  height: 180px;   /* 원문 수치 유지 */
}
```

**D-Day 뱃지**
```css
.badge-dday {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 var(--space-2);
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: #FFFFFF;
  font-size: var(--text-xs);
  font-weight: var(--fw-bold);
}
.badge-dday.today  { background: var(--color-error); }
.badge-dday.past   { background: var(--color-text-muted); }
```

---

### 3.3 스토어 카드 그리드

```css
.store-grid {
  display: grid;
  grid-template-columns: repeat(3, 210px);  /* 원문 3열, 카드폭 210px 유지 */
  gap: var(--space-6);                       /* 24px */
}

.store-card {
  width: 210px;
  border-radius: var(--radius-lg);
  background: var(--color-bg-card);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
  transition: box-shadow var(--transition-base), transform var(--transition-base);
  cursor: pointer;
}
.store-card:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.store-card__image {
  width: 210px;
  height: 210px;   /* 원문 1:1 비율 유지 */
  object-fit: cover;
  display: block;
}

.store-card__body {
  padding: var(--space-3) var(--space-3) var(--space-4);
  /* 이미지 하단 12px 여백은 padding-top: 12px로 처리 */
}

/* 콘텐츠 순서: 인증 마크 → 가게명 → 가격 */
.store-card__merchant-badge {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  margin-bottom: var(--space-1);
  font-size: var(--text-xs);
  color: var(--color-secondary-dark);
}
/* 인증 마크 아이콘은 엽전 골드(--color-secondary) 사용 */

.store-card__name {
  font-size: var(--text-sm);      /* 14px */
  font-weight: var(--fw-bold);
  color: var(--color-text-primary);
  margin-bottom: var(--space-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.store-card__price {
  font-size: var(--text-base);    /* 16px */
  font-weight: var(--fw-bold);
  font-family: var(--font-mono);
  color: var(--color-primary);
}
```

---

### 3.4 채팅 UI

```css
.chat-layout {
  display: flex;
  height: calc(100vh - 72px);
}

.chat-room-list {
  width: 280px;
  border-right: 1px solid var(--color-border);
  overflow-y: auto;
}

.chat-messages {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-message-area {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-4) var(--space-6);
}

.chat-input-bar {
  height: 64px;
  padding: 0 var(--space-4);
  border-top: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  gap: var(--space-3);
  background: var(--color-bg-card);
}
```

**메시지 버블**
```css
.message-bubble {
  max-width: 60%;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-xl);
  font-size: var(--text-sm);
  line-height: 1.6;
}
.message-bubble.mine {
  background: var(--color-primary);
  color: #FFFFFF;
  border-bottom-right-radius: var(--radius-sm);
  align-self: flex-end;
}
.message-bubble.others {
  background: var(--color-bg-base);
  color: var(--color-text-primary);
  border-bottom-left-radius: var(--radius-sm);
  align-self: flex-start;
}

/* 번역 토글 버튼 (채팅 전용 기능) */
.translate-toggle {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
  cursor: pointer;
  margin-top: var(--space-1);
  text-decoration: underline;
}
```

---

## 4. 공통 컴포넌트 규격

### 4.1 버튼

```css
/* 기본 크기 (md) */
.btn {
  height: 44px;
  padding: 0 var(--space-6);
  border-radius: var(--radius-md);
  font-size: var(--text-base);
  font-weight: var(--fw-medium);
  cursor: pointer;
  transition: background var(--transition-fast), transform var(--transition-fast);
  border: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
}
.btn:active { transform: scale(0.98); }

/* 크기 변형 */
.btn-sm  { height: 36px; padding: 0 var(--space-4); font-size: var(--text-sm); }
.btn-lg  { height: 52px; padding: 0 var(--space-8); font-size: var(--text-lg); }

/* 색상 변형 */
.btn-primary   { background: var(--color-primary); color: #FFFFFF; }
.btn-primary:hover { background: var(--color-primary-light); }
.btn-primary:active { background: var(--color-primary-dark); }

.btn-outline   { background: transparent; color: var(--color-primary); border: 1.5px solid var(--color-primary); }
.btn-outline:hover { background: var(--color-primary-subtle); }

.btn-ghost     { background: transparent; color: var(--color-text-secondary); }
.btn-ghost:hover { background: var(--color-bg-base); }

.btn:disabled  { opacity: 0.4; cursor: not-allowed; transform: none; }
```

---

### 4.2 입력 필드 (Input)

```css
.input {
  height: 48px;
  width: 100%;
  padding: 0 var(--space-4);
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: var(--text-base);
  font-family: var(--font-primary);
  color: var(--color-text-primary);
  background: var(--color-bg-card);
  outline: none;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}
.input:focus {
  border-color: var(--color-border-focus);
  box-shadow: 0 0 0 3px rgba(255, 107, 53, 0.15);
}
.input::placeholder { color: var(--color-text-muted); }
.input.error { border-color: var(--color-error); }
```

---

### 4.3 엽전 UI (전용 컴포넌트)

> 엽전 관련 UI는 반드시 `--color-secondary` (골드)를 사용하고, 다른 UI와 시각적으로 구분한다.

```css
.yeopjeon-badge {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-1) var(--space-3);
  background: #FFF8E1;
  border: 1px solid var(--color-secondary);
  border-radius: var(--radius-full);
  font-size: var(--text-sm);
  font-weight: var(--fw-bold);
  font-family: var(--font-mono);
  color: #7C5C00;
}
.yeopjeon-badge::before {
  content: '🪙';
  font-size: var(--text-base);
}

/* 엽전 잔액 표시 (마이페이지, 결제 화면 등) */
.yeopjeon-balance {
  font-size: var(--text-2xl);
  font-weight: var(--fw-bold);
  font-family: var(--font-mono);
  color: #7C5C00;
}
```

---

### 4.4 상인 인증 마크 (Merchant Verified Badge)

```css
.merchant-verified {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: var(--text-xs);
  font-weight: var(--fw-medium);
  color: var(--color-secondary-dark);
}
/* 아이콘: ✓ 또는 방패 아이콘, 색상은 --color-secondary */
.merchant-verified-icon {
  color: var(--color-secondary);
  font-size: 14px;
}
```

---

### 4.5 토스트 알림

```css
.toast {
  position: fixed;
  bottom: var(--space-6);
  right: var(--space-6);
  min-width: 280px;
  max-width: 380px;
  padding: var(--space-4) var(--space-5);
  background: var(--color-text-primary);
  color: #FFFFFF;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  font-size: var(--text-sm);
  z-index: var(--z-toast);
  /* 진입: translateY(80px) → translateY(0), 퇴장: opacity 0 */
  animation: slideUp 250ms ease forwards;
}
@keyframes slideUp {
  from { transform: translateY(80px); opacity: 0; }
  to   { transform: translateY(0);    opacity: 1; }
}
```

---

### 4.6 스켈레톤 로딩

```css
.skeleton {
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
  border-radius: var(--radius-md);
}
@keyframes shimmer {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
```

---

## 5. 반응형 (Responsive / PWA)

### 5.1 미디어 쿼리 중단점

```css
/* Desktop */
@media (min-width: 1024px) { /* 3단 레이아웃, 분할 레이아웃 */ }

/* Tablet — 사이드바 접힘, 2단 전환 */
@media (min-width: 768px) and (max-width: 1023px) {
  /* 좌측 LNB → 드로어, 우측 사이드바 → 숨김 */
  /* 메인 콘텐츠 max-width: 100% */
}

/* Mobile / PWA */
@media (max-width: 767px) { /* 1단 세로 스택 */ }
```

---

### 5.2 모바일 레이아웃 변형

**하단 탭바 (Bottom Navigation Bar)**
```css
.bottom-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 60px;
  background: var(--color-bg-card);
  border-top: 1px solid var(--color-border);
  display: flex;
  justify-content: space-around;
  align-items: center;
  z-index: var(--z-sticky);
  /* PWA 홈 인디케이터 영역 보상 */
  padding-bottom: env(safe-area-inset-bottom);
}

/* 탭 항목: 홈 | 지도 | 축제 | 스토어 | 마이페이지 */
.bottom-nav__item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  font-size: 10px;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: color var(--transition-fast);
  min-height: 44px;   /* 터치 타겟 최소 크기 (Apple HIG 기준) */
}
.bottom-nav__item.active {
  color: var(--color-primary);
}
.bottom-nav__item svg { width: 22px; height: 22px; }

/* 모바일에서 헤더 GNB 숨김 처리 */
@media (max-width: 767px) {
  .gnb-menu         { display: none; }
  .bottom-nav       { display: flex; }
  body              { padding-top: 60px; padding-bottom: 60px; }
}
```

---

**바텀 시트 (Bottom Sheet — 지도 모바일 전용)**
```css
.bottom-sheet {
  position: fixed;
  bottom: 60px;      /* 하단 탭바 위에 위치 */
  left: 0;
  right: 0;
  background: var(--color-bg-card);
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
  box-shadow: var(--shadow-lg);
  z-index: var(--z-overlay);
  transition: height var(--transition-slow);
  overflow: hidden;
}
.bottom-sheet[data-state="collapsed"] { height: 120px; }   /* 원문 기본 높이 */
.bottom-sheet[data-state="expanded"]  { height: calc(100vh - 60px - 60px); }

/* 드래그 핸들 */
.bottom-sheet__handle {
  width: 36px;
  height: 4px;
  background: var(--color-border);
  border-radius: var(--radius-full);
  margin: var(--space-3) auto var(--space-2);
  cursor: grab;
}
```

---

### 5.3 모바일 스토어 카드 그리드

```css
@media (max-width: 767px) {
  .store-grid {
    grid-template-columns: repeat(2, 1fr);  /* 2열로 전환 */
    gap: var(--space-4);
  }
  .store-card__image {
    width: 100%;
    height: 0;
    padding-bottom: 100%;  /* 1:1 비율 유지 */
    position: relative;
  }
}
```

---

## 6. 접근성 체크리스트 (AI 구현 시 필수 확인)

| 항목 | 규칙 |
|------|------|
| 포커스 링 | 모든 인터랙티브 요소에 `outline: 2px solid var(--color-border-focus)` 포커스 스타일 적용 |
| 아이콘 라벨 | 단독 아이콘 버튼에는 반드시 `aria-label` 또는 `<span class="sr-only">` 레이블 포함 |
| 색상 대비 | 본문 텍스트 최소 4.5:1, 대형 텍스트(18px bold+) 최소 3:1 (WCAG AA) |
| 최소 터치 영역 | 모바일 모든 인터랙티브 요소 `min-height: 44px; min-width: 44px` |
| 스크린 리더 | 지도 영역에 `aria-label="관광지 지도"` 및 대체 리스트 뷰 제공 |
| 언어 속성 | 채팅 번역 전환 시 `lang` 속성 동적 변경 (`ko` → `en` 등) |

---

## 7. 금지 사항 (DO NOT)

> AI가 구현 시 절대 위반하지 말아야 할 규칙

1. **`--color-secondary` (골드)를 엽전·뱃지·인증 마크 외 UI에 사용하지 않는다.**
2. **본문 텍스트 크기 14px 미만을 사용하지 않는다.**
3. **지도 화면에서 오버레이 팝업(InfoWindow) 위에 복잡한 정보를 중첩하지 않는다.**
4. **임의로 Purple, Teal 등 정의되지 않은 브랜드 컬러를 추가하지 않는다.**
5. **버튼, 입력 필드에 `border-radius` 이외의 그림자 장식을 추가하지 않는다.**
6. **엽전 잔액·가격에 `--font-mono` 이외의 폰트를 사용하지 않는다.**
7. **모바일 환경에서 하단 탭바 없이 상단 GNB만으로 내비게이션을 구성하지 않는다.**

---

## 8. 컴포넌트 구현 우선순위

| 우선순위 | 컴포넌트 | 이유 |
|----------|----------|------|
| P0 (즉시) | Header, BottomNav, 버튼, 입력 필드, 카드 | 모든 화면 공통 |
| P0 (즉시) | 지도 Split Layout, 퀵 필터 칩 | MVP 핵심 기능 |
| P1 | 스토어 카드 그리드, 엽전 UI | 스토어·결제 도메인 |
| P1 | 축제 캘린더 그리드, 슬라이드 패널 | 캘린더 도메인 |
| P1 | 채팅 버블, 번역 토글 | 채팅 도메인 |
| P2 | 바텀 시트, 모바일 드로어 | PWA 경험 |
| P2 | 스켈레톤 로딩, 토스트 알림 | 공통 피드백 |
| P3 | 뱃지, 쿠폰, 프로필 레벨 | MVP 이후 확장 |

---

*본 문서는 춘배투어 프론트엔드 구현의 유일한 디자인 기준 문서입니다. 구현 중 새로운 패턴이 필요할 경우 이 문서에 추가한 후 사용합니다.*
