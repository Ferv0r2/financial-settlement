# PayFlow 스프린트 계획

> 총 5개 Phase, 10주 계획

---

## 진행 현황

| Phase | 상태 | 진행률 |
|-------|------|--------|
| Phase 1 | 🔵 진행 중 | 0% |
| Phase 2 | ⚪ 대기 | 0% |
| Phase 3 | ⚪ 대기 | 0% |
| Phase 4 | ⚪ 대기 | 0% |
| Phase 5 | ⚪ 대기 | 0% |

---

## Phase 1: 프로젝트 기반 구축 (Week 1-2)

### Sprint 1.1: 인프라 및 프로젝트 구조

- [ ] Docker Compose 환경 구성 (PostgreSQL, Redis)
- [ ] 멀티 모듈 구조 정리 (common, api, batch)
- [ ] 공통 설정 (application.yml profiles: local, test)
- [ ] 공통 예외 처리 및 응답 포맷 정의
- [ ] Flyway 마이그레이션 설정

**산출물:** Docker 환경에서 Spring Boot 앱 정상 구동

---

### Sprint 1.2: 도메인 모델 및 결제 기본 기능

- [ ] Payment 엔티티 + Repository
- [ ] PaymentStatus Enum 및 상태 머신 구현
- [ ] 상태 전이 검증 로직 (불가능한 전이 차단)
- [ ] Merchant 엔티티 (기본)
- [ ] AuditLog 엔티티 + 자동 기록 (JPA EventListener)

**산출물:** Payment 상태 머신 단위 테스트 통과

---

### Sprint 1.3: 결제 API 기본 구현

- [ ] `POST /api/v1/payments` - 결제 생성
- [ ] `GET /api/v1/payments/{id}` - 결제 조회
- [ ] `GET /api/v1/payments/{id}/history` - 히스토리 조회
- [ ] 입력 검증 (Bean Validation)
- [ ] Swagger/OpenAPI 설정

**산출물:** 결제 생성/조회 API 정상 동작

---

## Phase 2: 핵심 안정성 기능 (Week 3-4)

### Sprint 2.1: Mock PG 구현

- [ ] MockPgClient 인터페이스 정의
- [ ] 카드번호 기반 시나리오 구현
  - `1111-...`: 잔액 부족
  - `2222-...`: 카드 만료
  - `9999-...`: 타임아웃
  - 기타: 정상 승인
- [ ] Mock 제어 API (`PUT /api/v1/mock/pg/config`)
- [ ] 응답 지연 시뮬레이션

**산출물:** Mock PG 시나리오별 테스트 통과

---

### Sprint 2.2: 멱등성 처리

- [ ] Redis 연동 (Lettuce)
- [ ] IdempotencyService 구현
  - 첫 요청: 처리 후 결과 캐시
  - 재요청: 캐시된 결과 반환
  - 처리 중 재요청: 202 Accepted
- [ ] `Idempotency-Key` 헤더 필수화
- [ ] TTL 24시간 설정

**산출물:** 중복 결제 100% 방지 테스트 통과

---

### Sprint 2.3: 동시성 제어

- [ ] Redisson 분산 락 구현
  - Lock key: `payment:order:{orderId}`
  - 획득 대기: 3초, 유지: 10초
- [ ] JPA `@Version` 낙관적 락 적용
- [ ] 동시성 테스트 (10 threads, 같은 orderId)

**산출물:** 동시 결제 시 1건만 성공 검증

---

### Sprint 2.4: 수수료 계산 및 가맹점 정책

- [ ] MerchantFeePolicy 엔티티
- [ ] FeeCalculator 서비스
  - 수수료 = 결제금액 × 수수료율
  - VAT = 수수료 × 10%
  - 정산금 = 결제금액 - 수수료 - VAT
- [ ] 결제 수단별 기본 수수료율 설정
- [ ] 가맹점별 차등 수수료 적용

**산출물:** 수수료 계산 경계값 테스트 통과

---

## Phase 3: 청산/정산 프로세스 (Week 5-6)

### Sprint 3.1: 결제 취소 기능

- [ ] `POST /api/v1/payments/{id}/cancel` - 전체 취소
- [ ] `POST /api/v1/payments/{id}/partial-cancel` - 부분 취소
- [ ] 취소 가능 상태 검증 (APPROVED만 가능)
- [ ] PartialCancellation 엔티티 (부분 취소 이력)

**산출물:** 취소 API 및 상태 전이 테스트 통과

---

### Sprint 3.2: 청산 배치 (Clearing)

- [ ] Spring Batch 설정
- [ ] ClearingJob 구현 (매일 자정 실행)
  - APPROVED → CLEARED 상태 변경
  - 가맹점별 집계
  - Settlement 엔티티 생성
- [ ] 청산 범위 관리 (cut-off 시간)
- [ ] 청산 내역서 생성

**산출물:** 배치 실행 후 Settlement 레코드 생성 확인

---

### Sprint 3.3: 정산 실행 (Settlement)

- [ ] Mock Bank 구현
  - 기본: 500ms 지연 후 성공
  - 점검 시간: BANK_MAINTENANCE 에러
  - 계좌 오류: INVALID_ACCOUNT 에러
- [ ] SettlementService 구현
  - CLEARED → SETTLED 상태 변경
  - 재시도 로직 (Exponential Backoff)
- [ ] Dead Letter Queue 처리

**산출물:** 정산 성공/실패/재시도 시나리오 테스트 통과

---

### Sprint 3.4: 역정산 (환불)

- [ ] `POST /api/v1/payments/{id}/refund` - 환불 요청
- [ ] RefundService 구현
  - SETTLED 상태에서만 환불 가능
  - 가맹점 잔액 확인 로직
  - REFUND_PENDING 상태 처리
- [ ] 다음 정산에서 차감 로직

**산출물:** 역정산 전체 흐름 테스트 통과

---

## Phase 4: 대사 및 관리자 화면 (Week 7-8)

### Sprint 4.1: 정산 대사 (Reconciliation)

- [ ] Mock Bank 입금 내역 생성 (시뮬레이션)
- [ ] ReconciliationService 구현
  - 우리 정산금 vs 은행 입금 비교
  - MATCHED / MISMATCHED 상태
- [ ] 불일치 탐지 및 알림
- [ ] 수동 조정 API

**산출물:** 대사 일치/불일치 케이스 테스트 통과

---

### Sprint 4.2: 관리자 API

- [ ] `GET /api/v1/admin/dashboard` - 실시간 현황
- [ ] `GET /api/v1/admin/transactions` - 트랜잭션 검색
- [ ] `GET /api/v1/admin/audit-logs` - 감사 로그 조회
- [ ] Merchant CRUD API 완성
- [ ] 통계 조회 API (일별/월별)

**산출물:** 관리자 API 전체 동작 확인

---

### Sprint 4.3: 관리자 대시보드 (Frontend)

- [ ] React 프로젝트 셋업 (Vite + TypeScript)
- [ ] TailwindCSS 스타일링
- [ ] 실시간 모니터링 화면
  - 결제 현황 차트
  - 성공/실패율
  - TPS 모니터링
- [ ] 트랜잭션 검색/상세 화면
- [ ] Mock 시나리오 제어 화면

**산출물:** 대시보드 기본 화면 구현

---

### Sprint 4.4: 인증/인가 및 보안

- [ ] Spring Security 설정
- [ ] JWT 발급/검증 (Access + Refresh Token)
- [ ] RBAC 구현 (SYSTEM_ADMIN, MERCHANT_ADMIN, etc.)
- [ ] Rate Limiting (Bucket4j)
- [ ] 입력 검증 강화

**산출물:** 역할별 접근 제어 테스트 통과

---

## Phase 5: 테스트 및 문서화 (Week 9-10)

### Sprint 5.1: 통합 테스트 강화

- [ ] Testcontainers 환경 구성
- [ ] 전체 흐름 E2E 테스트
  - Payment → Clearing → Settlement → Reconciliation
- [ ] 역정산 E2E 테스트
- [ ] 테스트 커버리지 80% 달성

**산출물:** 통합 테스트 전체 통과

---

### Sprint 5.2: 부하 테스트

- [ ] Gatling 시나리오 작성
- [ ] 100 TPS 목표 테스트
- [ ] 응답 시간 측정 (p50, p99)
- [ ] 병목 구간 식별 및 최적화

**산출물:** 성능 테스트 리포트

---

### Sprint 5.3: 보안 테스트 및 감사

- [ ] OWASP ZAP 스캔
- [ ] SQL Injection 테스트
- [ ] XSS 테스트
- [ ] 민감 데이터 노출 점검
- [ ] 감사 로그 무결성 검증

**산출물:** 보안 테스트 리포트

---

### Sprint 5.4: 문서화 및 최종 정리

- [ ] README.md 완성
- [ ] API 문서 (Swagger) 최종 정리
- [ ] 아키텍처 다이어그램 (draw.io/Mermaid)
- [ ] ERD 다이어그램
- [ ] 개발 가이드 (설치, 실행, 테스트)
- [ ] ADR (Architecture Decision Records)

**산출물:** 완성된 프로젝트 문서

---

## 우선순위 정리

### P0 (필수)
1. 결제 생성/조회/취소
2. 상태 머신
3. 멱등성 처리
4. 동시성 제어
5. 수수료 계산
6. 청산/정산 배치

### P1 (중요)
1. Mock PG/Bank 시나리오
2. 역정산
3. 정산 대사
4. 감사 로깅
5. 관리자 대시보드

### P2 (선택)
1. 부분 취소
2. FDS 기본 룰
3. 상세 통계
4. 알림 시스템

---

**— End of Document —**
