# PayFlow - Product Requirements Document

> **결제/정산 시스템 캡스톤 프로젝트**

| 항목 | 내용 |
|------|------|
| Version | 2.0 |
| 작성일 | 2024년 12월 |
| 작성자 | 황원태 |
| 프로젝트 유형 | 결제/정산 시스템 학습용 캡스톤 프로젝트 |

---

## 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [시스템 아키텍처](#2-시스템-아키텍처)
3. [기능 요구사항](#3-기능-요구사항)
4. [수수료 및 정산 정책](#4-수수료-및-정산-정책)
5. [목업 환경 설계](#5-목업-환경-설계)
6. [비기능 요구사항](#6-비기능-요구사항)
7. [보안 요구사항](#7-보안-요구사항)
8. [API 설계](#8-api-설계)
9. [데이터 모델](#9-데이터-모델)
10. [테스트 전략](#10-테스트-전략)
11. [구현 로드맵](#11-구현-로드맵)
12. [성공 지표](#12-성공-지표)

---

## 1. 프로젝트 개요

### 1.1 배경

금융권에서 결제 시스템은 핵심 인프라입니다. 이 프로젝트는 실제 금융권에서 사용되는 **Payment → Clearing → Settlement** 흐름을 간소화하여 구현하며, 트랜잭션 안정성, 멱등성, 동시성 제어 등 금융 도메인의 핵심 개념을 직접 경험하는 것을 목표로 합니다.

### 1.2 프로젝트 목표

1. 실제 금융권에서 사용되는 결제/정산 프로세스의 핵심 개념 학습 및 구현
2. Java/Spring Boot 기반 백엔드 개발 역량 향상
3. 목업 환경에서 다양한 시나리오를 시뮬레이션하여 시스템 검증
4. 트랜잭션 안정성, 멱등성, 동시성 제어 등 금융 도메인 핵심 기술 습득

### 1.3 핵심 용어 정의

| 용어 | 정의 |
|------|------|
| **Payment** | 결제 요청을 받아 처리하는 단계. 카드사/은행 승인 요청 |
| **Clearing** | 결제 완료 건들을 집계하고 정산 금액을 계산하는 단계 |
| **Settlement** | 실제 자금 이체가 발생하는 최종 단계 |
| **Idempotency** | 동일한 요청을 여러 번 보내도 결과가 한 번만 적용되는 특성 |
| **Reconciliation** | 정산 대사. 우리 시스템과 실제 은행 입출금 내역 일치 확인 |
| **Reverse Settlement** | 역정산. 정산 완료 후 환불 시 가맹점에서 금액 회수 |

---

## 2. 시스템 아키텍처

### 2.1 전체 흐름

```
┌─────────┐     ┌─────────────┐     ┌─────────────────┐     ┌──────────┐
│ Client  │────▶│ API Gateway │────▶│ Payment Service │────▶│ Mock PG  │
└─────────┘     └─────────────┘     └─────────────────┘     └──────────┘
                      │                      │
                      ▼                      ▼
               ┌────────────┐         ┌────────────┐
               │   Redis    │         │ PostgreSQL │
               │ (분산 락)   │         │  (ACID)    │
               └────────────┘         └────────────┘
                                            │
                      ┌─────────────────────┴─────────────────────┐
                      ▼                                           ▼
              ┌───────────────┐                          ┌───────────────┐
              │   Clearing    │◀── Scheduler (자정) ──▶  │    Audit      │
              │   Service     │                          │   Service     │
              └───────────────┘                          └───────────────┘
                      │
                      ▼
              ┌───────────────┐     ┌────────────┐
              │  Settlement   │────▶│ Mock Bank  │
              │   Service     │     └────────────┘
              └───────────────┘
                      │
                      ▼
              ┌───────────────┐
              │Reconciliation │
              │   Service     │
              └───────────────┘
```

### 2.2 상태 머신 (State Machine)

결제 트랜잭션은 다음 상태를 순차적으로 거칩니다:

```
                    ┌──────────────┐
                    │  REQUESTED   │ 결제 요청 접수 완료
                    └──────┬───────┘
                           │
                           ▼
                    ┌──────────────┐
              ┌─────│  PROCESSING  │ PG사 승인 요청 중
              │     └──────┬───────┘
              │            │
              ▼            ▼
       ┌──────────┐  ┌──────────────┐
       │  FAILED  │  │   APPROVED   │ PG사 승인 완료
       └──────────┘  └──────┬───────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
              ▼            ▼            ▼
       ┌──────────┐  ┌──────────────┐  │
       │CANCELLED │  │   CLEARED    │  │ 정산 대상으로 집계됨
       └──────────┘  └──────┬───────┘  │
                           │            │
                           ▼            │
                    ┌──────────────┐    │
                    │   SETTLED    │◀───┘ 자금 이체 완료
                    └──────┬───────┘
                           │
                           ▼
                    ┌──────────────┐
                    │   REFUNDED   │ 환불 완료 (역정산)
                    └──────────────┘
```

#### 상태 전이 규칙

| 현재 상태 | 가능한 전이 | 불가능한 전이 |
|----------|------------|--------------|
| REQUESTED | PROCESSING | 다른 모든 상태 |
| PROCESSING | APPROVED, FAILED | REQUESTED, CLEARED, SETTLED |
| APPROVED | CLEARED, CANCELLED | REQUESTED, PROCESSING |
| CLEARED | SETTLED | REQUESTED, PROCESSING, APPROVED |
| SETTLED | REFUNDED | 다른 모든 상태 |
| FAILED | - (최종 상태) | 모든 상태 |
| CANCELLED | - (최종 상태) | 모든 상태 |
| REFUNDED | - (최종 상태) | 모든 상태 |

### 2.3 기술 스택

| 레이어 | 기술 |
|--------|------|
| **Backend** | Java 17, Spring Boot 3.x, Spring Data JPA |
| **Database** | PostgreSQL (ACID 트랜잭션), Redis (분산 락, 캐싱, 멱등성 키) |
| **Frontend** | React, TypeScript, TailwindCSS (관리자 대시보드) |
| **Infra** | Docker, Docker Compose |
| **Testing** | JUnit 5, Mockito, Testcontainers, Gatling |
| **Monitoring** | Micrometer, Actuator, (Optional: Grafana) |

---

## 3. 기능 요구사항

### 3.1 결제 처리 (Payment)

| ID | 기능 | 상세 |
|----|------|------|
| PAY-001 | 결제 요청 생성 | 주문 정보와 결제 수단을 받아 새로운 결제 요청 생성 |
| PAY-002 | 멱등성 키 검증 | `Idempotency-Key` 헤더로 중복 결제 방지 (TTL: 24시간) |
| PAY-003 | Mock PG 승인 요청 | 가상 결제 게이트웨이에 승인 요청 |
| PAY-004 | 결제 취소 | APPROVED 상태의 결제 건 취소 처리 (당일 취소) |
| PAY-005 | 결제 상태 조회 | 결제 ID로 현재 상태 및 히스토리 조회 |
| PAY-006 | 부분 취소 | 결제 금액의 일부만 취소 |

#### 멱등성 처리 상세

```
Case 1: 첫 요청
  → 결제 생성 및 처리, 결과 반환

Case 2: 동일 키 + 동일 금액 재요청
  → 기존 결과 반환 (새 결제 생성 안 함)

Case 3: 동일 키 + 다른 금액 재요청
  → 409 Conflict 에러 (금액 불일치)

Case 4: 처리 중 재요청
  → 202 Accepted + Retry-After 헤더 (폴링 유도)
```

### 3.2 청산 처리 (Clearing)

| ID | 기능 | 상세 |
|----|------|------|
| CLR-001 | 배치 청산 | 일정 주기(매일 자정)로 APPROVED 상태 건들을 CLEARED로 변경 |
| CLR-002 | 가맹점별 집계 | 가맹점별로 결제 금액 합산 |
| CLR-003 | 수수료 계산 | 가맹점별 수수료 정책에 따른 수수료 계산 |
| CLR-004 | 청산 내역서 생성 | 가맹점별 청산 내역 리포트 생성 |
| CLR-005 | 청산 범위 관리 | 청산 배치 중 신규 결제는 다음 청산에 포함 |

### 3.3 정산 처리 (Settlement)

| ID | 기능 | 상세 |
|----|------|------|
| STL-001 | 정산 실행 | 청산 완료 건에 대해 Mock Bank로 자금 이체 요청 |
| STL-002 | 정산 완료 처리 | 이체 성공 시 SETTLED 상태로 변경 |
| STL-003 | 정산 실패 재시도 | 이체 실패 시 지수 백오프로 재시도 (최대 5회) |
| STL-004 | Dead Letter Queue | 최대 재시도 초과 시 DLQ로 이동 및 알림 |
| STL-005 | 역정산 (환불) | 정산 완료 후 환불 요청 시 가맹점에서 금액 회수 |

### 3.4 정산 대사 (Reconciliation)

| ID | 기능 | 상세 |
|----|------|------|
| RCN-001 | 일일 대사 | 우리 시스템 정산 금액 vs 은행 입금 내역 비교 |
| RCN-002 | 불일치 탐지 | 금액 불일치 시 자동 알림 및 RECONCILIATION_PENDING 상태 |
| RCN-003 | 수동 조정 | 관리자 화면에서 불일치 건 수동 처리 |

### 3.5 관리자 대시보드

| ID | 기능 | 상세 |
|----|------|------|
| ADM-001 | 실시간 모니터링 | 결제 현황, 성공/실패율, 처리량 시각화 |
| ADM-002 | 트랜잭션 검색 | 결제 ID, 가맹점, 상태별 검색 |
| ADM-003 | Mock 시나리오 제어 | 결제 성공/실패/지연 시나리오 설정 |
| ADM-004 | 감사 로그 조회 | 모든 상태 변경 이력 조회 |
| ADM-005 | 가맹점 관리 | 가맹점 등록, 수수료율 설정, 정산 주기 설정 |

---

## 4. 수수료 및 정산 정책

### 4.1 수수료 체계

#### 기본 수수료 구조

```java
정산금액 = 결제금액 - 수수료 - VAT

수수료 = 결제금액 × 수수료율
VAT = 수수료 × 10%
```

#### 결제 수단별 수수료율

| 결제 수단 | 기본 수수료율 | 비고 |
|----------|-------------|------|
| 신용카드 | 3.3% | 할부 시 추가 수수료 |
| 체크카드 | 1.5% | - |
| 계좌이체 | 1.0% | - |
| 가상계좌 | 2.0% | 건당 고정 수수료 추가 가능 |

#### 가맹점별 차등 수수료

```sql
CREATE TABLE merchant_fee_policies (
    id BIGSERIAL PRIMARY KEY,
    merchant_id VARCHAR(32) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,  -- CREDIT_CARD, DEBIT_CARD, etc.
    fee_rate DECIMAL(5,4) NOT NULL,       -- 0.0330 = 3.3%
    fixed_fee DECIMAL(10,2) DEFAULT 0,    -- 건당 고정 수수료
    min_fee DECIMAL(10,2),                -- 최소 수수료
    max_fee DECIMAL(10,2),                -- 최대 수수료
    vat_inclusive BOOLEAN DEFAULT FALSE,  -- VAT 포함 여부
    effective_from DATE NOT NULL,
    effective_to DATE,
    UNIQUE(merchant_id, payment_method, effective_from)
);
```

### 4.2 정산 주기

| 정산 유형 | 주기 | 대상 가맹점 |
|---------|------|-----------|
| D+1 | 익일 정산 | 대형 가맹점, 신뢰도 높은 가맹점 |
| D+3 | 3일 후 정산 | 일반 가맹점 |
| D+7 | 7일 후 정산 | 신규 가맹점, 고위험 업종 |
| Weekly | 주간 정산 | 소규모 가맹점 |
| Monthly | 월간 정산 | 특수 계약 가맹점 |

### 4.3 지급 보류금

```java
// 반품/환불 대비 일정 비율 보류
class SettlementPolicy {
    BigDecimal reserveRate;      // 보류율 (예: 10%)
    int reserveReleaseDays;      // 보류 해제 기간 (예: 14일)
}
```

### 4.4 역정산 (Refund Settlement)

```
정산 완료 후 환불 시나리오:

Day 1, 10:00 - 고객 10만원 결제 (APPROVED)
Day 2, 00:00 - 청산 배치 (CLEARED)
Day 2, 09:00 - 정산 완료 (SETTLED) - 가맹점 계좌 입금
Day 3, 15:00 - 고객 환불 요청

처리 방법:
1. REFUND_REQUESTED 상태로 변경
2. 가맹점 계좌 잔액 확인
   - 잔액 충분: 즉시 출금 후 고객 환불
   - 잔액 부족: 다음 정산에서 차감 (REFUND_PENDING)
3. 환불 완료 시 REFUNDED 상태로 변경
4. AuditLog 기록
```

---

## 5. 목업 환경 설계

실제 PG사/은행 연동 없이도 시스템을 충분히 검증할 수 있도록 Mock 환경을 구성합니다.

### 5.1 Mock Payment Gateway

#### 카드번호 기반 시나리오

| 카드번호 | 시나리오 | 응답 |
|---------|---------|------|
| `1111-1111-1111-1111` | 잔액 부족 | `INSUFFICIENT_BALANCE` |
| `2222-2222-2222-2222` | 카드 만료 | `EXPIRED_CARD` |
| `3333-3333-3333-3333` | 도난 카드 | `STOLEN_CARD` |
| `4444-4444-4444-4444` | CVV 오류 | `INVALID_CVV` |
| `5555-5555-5555-5555` | 3D-Secure 필요 | `AUTHENTICATION_REQUIRED` |
| `9999-9999-9999-9999` | 타임아웃 | 5초 지연 후 `TIMEOUT` |
| 기타 | 정상 승인 | 200ms 지연 후 `APPROVED` |

#### 금액 기반 추가 시나리오

| 조건 | 시나리오 |
|------|---------|
| 금액 >= 100만원 | FDS 검증 필요 (추가 200ms 지연) |
| 금액 >= 500만원 | 추가 인증 필요 |
| 금액 끝자리 01 | 잔액 부족 (레거시 호환) |
| 금액 끝자리 02 | 카드 만료 (레거시 호환) |

#### 확률 기반 랜덤 실패

```yaml
mock:
  pg:
    success-rate: 0.99      # 99% 성공
    timeout-rate: 0.005     # 0.5% 타임아웃
    error-rate: 0.005       # 0.5% 랜덤 에러
    average-delay-ms: 200   # 평균 응답 지연
```

### 5.2 Mock Bank (정산용)

| 시나리오 | 트리거 | 동작 |
|---------|-------|------|
| 정상 이체 | 기본 동작 | 500ms 지연 후 이체 완료 응답 |
| 은행 점검 (평일) | 23:30~00:30 | `BANK_MAINTENANCE` 에러 |
| 은행 점검 (주말) | 토 23:30 ~ 일 08:00 | `BANK_MAINTENANCE` 에러 |
| 이체 한도 초과 | 일일 누적 1억원 초과 | `DAILY_LIMIT_EXCEEDED` |
| 계좌 오류 | 수취 계좌 `0000-0000` | `INVALID_ACCOUNT` |
| 예금주 불일치 | 예금주명 `ERROR` | `HOLDER_MISMATCH` |

### 5.3 Mock 제어 API

```
PUT  /api/v1/mock/pg/config      - Mock PG 동작 설정
PUT  /api/v1/mock/bank/config    - Mock Bank 동작 설정
POST /api/v1/mock/reset          - Mock 환경 초기화
POST /api/v1/mock/scenarios/fraud-attack   - 이상거래 공격 시뮬레이션
POST /api/v1/mock/scenarios/bank-outage    - 은행 장애 시뮬레이션
```

---

## 6. 비기능 요구사항

### 6.1 트랜잭션 안정성

| 요구사항 | 구현 방법 |
|---------|----------|
| ACID 보장 | 결제 상태 변경은 원자적으로 처리 (Spring @Transactional) |
| 분산 락 | Redis를 이용한 동일 주문 동시 처리 방지 (Redisson) |
| 낙관적 락 | JPA @Version을 이용한 동시 수정 감지 |
| 비관적 락 | 잔액 차감 등 충돌 빈도 높은 작업에 SELECT FOR UPDATE |

#### 분산 락 설정

```java
// 락 획득 대기: 3초
// 락 유지 시간: 10초 (PG 응답 대기 + 버퍼)
RLock lock = redisson.getLock("payment:order:" + orderId);
boolean acquired = lock.tryLock(3, 10, TimeUnit.SECONDS);
```

### 6.2 멱등성

- 모든 결제 API는 `Idempotency-Key` 헤더 필수
- 동일 키로 재요청 시 기존 결과 반환 (새 결제 생성하지 않음)
- 멱등성 키 TTL: 24시간 (Redis 저장)
- 키 형식: UUID v4 권장 (`^[a-zA-Z0-9-]{16,64}$`)

### 6.3 재시도 정책

```java
@Retryable(
    value = {BankTemporaryException.class},
    maxAttempts = 5,
    backoff = @Backoff(
        delay = 1000,       // 초기 1초
        multiplier = 2,     // 2배씩 증가
        maxDelay = 30000    // 최대 30초
    )
)
public SettlementResult executeSettlement(ClearingData data) {
    return bankClient.transfer(data);
}

@Recover
public SettlementResult recover(Exception e, ClearingData data) {
    // DLQ로 이동 및 알림
    dlqPublisher.send(data);
    alertService.sendCritical("Settlement failed: " + data.getId());
    return SettlementResult.failed(data.getId());
}
```

### 6.4 감사 로깅 (Audit Trail)

- 모든 상태 변경에 대해 `AuditLog` 엔티티 생성
- 기록 항목: 변경 시각, 이전 상태, 이후 상태, 변경 사유, 요청자 정보
- 감사 로그는 수정/삭제 불가 (Append-only)
- 보관 기간: 10년 (금융 규제 준수)

### 6.5 성능 목표

| 지표 | 목표 |
|------|------|
| TPS | 100 TPS 이상 |
| 응답 시간 (P50) | < 200ms |
| 응답 시간 (P99) | < 500ms |
| 에러율 | < 1% |
| 가용성 | 99.9% |

---

## 7. 보안 요구사항

### 7.1 데이터 암호화

#### 저장 데이터 (Data at Rest)

| 데이터 | 처리 방법 |
|--------|----------|
| 카드번호 (PAN) | AES-256-GCM 암호화 저장 |
| CVV/CVC | **저장 금지** (PCI-DSS 요구사항) |
| 카드 표시 | 마스킹: `1234-56**-****-7890` |
| 개인정보 | 이름, 전화번호, 이메일 암호화 |

#### 전송 데이터 (Data in Transit)

- TLS 1.3 강제 사용
- HTTP → HTTPS 자동 리다이렉트
- HSTS 헤더 설정

### 7.2 인증/인가

#### JWT 설정

```yaml
jwt:
  access-token:
    expiration: 15m          # 15분
    algorithm: RS256         # 비대칭 암호화
  refresh-token:
    expiration: 7d           # 7일
    storage: httponly-cookie
    rotation: true           # Refresh Token Rotation
```

#### Role-Based Access Control (RBAC)

| 역할 | 권한 |
|------|------|
| `SYSTEM_ADMIN` | 전체 시스템 관리, Mock 설정 변경 |
| `MERCHANT_ADMIN` | 자사 가맹점 결제 조회, 정산 조회 |
| `MERCHANT_VIEWER` | 자사 가맹점 결제 조회만 |
| `AUDITOR` | 감사 로그 조회 전용 |

### 7.3 입력 검증

```java
public class PaymentRequest {
    @NotNull
    @Pattern(regexp = "^[a-zA-Z0-9-]{16,64}$",
             message = "Invalid idempotency key format")
    private String idempotencyKey;

    @NotNull
    @DecimalMin(value = "100", message = "Minimum amount is 100")
    @DecimalMax(value = "10000000", message = "Maximum amount is 10,000,000")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal amount;

    @NotNull
    @Pattern(regexp = "^[0-9]{13,19}$")
    private String cardNumber;

    @NotNull
    @Size(min = 32, max = 32)
    private String merchantId;
}
```

### 7.4 Rate Limiting

| 대상 | 제한 |
|------|------|
| IP 기반 | 10 req/sec |
| 가맹점별 | 100 req/sec |
| 전체 시스템 | 1000 req/sec |

### 7.5 이상거래 탐지 (FDS 기본 룰)

| 룰 ID | 조건 | 동작 |
|-------|------|------|
| FDS-001 | 10분 내 동일 카드 5회 이상 결제 | 자동 차단 |
| FDS-002 | 평소 결제 패턴 대비 10배 이상 금액 | 추가 인증 요구 |
| FDS-003 | 블랙리스트 카드번호 | 즉시 차단 |

---

## 8. API 설계

### 8.1 결제 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| `POST` | `/api/v1/payments` | 결제 요청 생성 |
| `GET` | `/api/v1/payments/{id}` | 결제 상태 조회 |
| `POST` | `/api/v1/payments/{id}/cancel` | 결제 취소 |
| `POST` | `/api/v1/payments/{id}/partial-cancel` | 부분 취소 |
| `GET` | `/api/v1/payments/{id}/history` | 상태 변경 히스토리 조회 |

#### 결제 요청 예시

```http
POST /api/v1/payments HTTP/1.1
Content-Type: application/json
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Authorization: Bearer {token}

{
  "merchantId": "MERCHANT_001",
  "orderId": "ORDER_20241206_001",
  "amount": 50000.00,
  "currency": "KRW",
  "paymentMethod": "CREDIT_CARD",
  "cardNumber": "1234-5678-9012-3456",
  "cardExpiry": "12/25",
  "customerName": "홍길동",
  "customerEmail": "hong@example.com"
}
```

#### 결제 응답 예시

```json
{
  "paymentId": "PAY_20241206_abcd1234",
  "status": "APPROVED",
  "amount": 50000.00,
  "fee": 1650.00,
  "netAmount": 48350.00,
  "approvalNumber": "12345678",
  "approvedAt": "2024-12-06T10:30:00Z",
  "merchantId": "MERCHANT_001",
  "orderId": "ORDER_20241206_001"
}
```

### 8.2 정산 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| `GET` | `/api/v1/settlements` | 정산 내역 조회 |
| `GET` | `/api/v1/settlements/{date}` | 특정일 정산 상세 |
| `GET` | `/api/v1/settlements/merchants/{merchantId}` | 가맹점별 정산 조회 |
| `POST` | `/api/v1/settlements/trigger` | 수동 정산 실행 (테스트용) |

### 8.3 대사 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| `GET` | `/api/v1/reconciliations/{date}` | 특정일 대사 결과 조회 |
| `GET` | `/api/v1/reconciliations/pending` | 미처리 불일치 건 조회 |
| `POST` | `/api/v1/reconciliations/{id}/resolve` | 불일치 건 수동 처리 |

### 8.4 가맹점 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| `POST` | `/api/v1/merchants` | 가맹점 등록 |
| `GET` | `/api/v1/merchants/{id}` | 가맹점 조회 |
| `PUT` | `/api/v1/merchants/{id}/fee-policy` | 수수료 정책 설정 |
| `PUT` | `/api/v1/merchants/{id}/settlement-cycle` | 정산 주기 설정 |

---

## 9. 데이터 모델

### 9.1 Payment (결제)

```sql
CREATE TABLE payments (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(64) UNIQUE NOT NULL,
    merchant_id VARCHAR(32) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'KRW',
    status VARCHAR(20) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    pg_transaction_id VARCHAR(64),
    approval_number VARCHAR(20),
    card_bin VARCHAR(6),              -- 카드 앞 6자리 (BIN)
    card_last4 VARCHAR(4),            -- 카드 뒤 4자리
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    approved_at TIMESTAMP,
    cancelled_at TIMESTAMP,

    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    INDEX idx_merchant_status (merchant_id, status),
    INDEX idx_created_at (created_at)
);
```

### 9.2 Settlement (정산)

```sql
CREATE TABLE settlements (
    id UUID PRIMARY KEY,
    merchant_id VARCHAR(32) NOT NULL,
    settlement_date DATE NOT NULL,
    total_amount DECIMAL(15,2) NOT NULL,
    total_fee DECIMAL(15,2) NOT NULL,
    total_vat DECIMAL(15,2) NOT NULL,
    net_amount DECIMAL(15,2) NOT NULL,
    payment_count INT NOT NULL,
    status VARCHAR(20) NOT NULL,        -- PENDING, PROCESSING, SETTLED, FAILED
    bank_transaction_id VARCHAR(64),
    settled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,

    UNIQUE(merchant_id, settlement_date)
);
```

### 9.3 Settlement Detail (정산 상세)

```sql
CREATE TABLE settlement_details (
    id BIGSERIAL PRIMARY KEY,
    settlement_id UUID NOT NULL REFERENCES settlements(id),
    payment_id UUID NOT NULL REFERENCES payments(id),
    gross_amount DECIMAL(15,2) NOT NULL,
    fee_amount DECIMAL(15,2) NOT NULL,
    vat_amount DECIMAL(15,2) NOT NULL,
    net_amount DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
```

### 9.4 Merchant Fee Policy (가맹점 수수료 정책)

```sql
CREATE TABLE merchant_fee_policies (
    id BIGSERIAL PRIMARY KEY,
    merchant_id VARCHAR(32) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    fee_rate DECIMAL(5,4) NOT NULL,
    fixed_fee DECIMAL(10,2) DEFAULT 0,
    min_fee DECIMAL(10,2),
    max_fee DECIMAL(10,2),
    vat_inclusive BOOLEAN DEFAULT FALSE,
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_at TIMESTAMP NOT NULL,

    UNIQUE(merchant_id, payment_method, effective_from)
);
```

### 9.5 Reconciliation (대사)

```sql
CREATE TABLE reconciliations (
    id UUID PRIMARY KEY,
    reconciliation_date DATE NOT NULL,
    our_total DECIMAL(15,2) NOT NULL,
    bank_total DECIMAL(15,2) NOT NULL,
    difference DECIMAL(15,2) NOT NULL,
    status VARCHAR(20) NOT NULL,        -- MATCHED, MISMATCHED, RESOLVED
    resolved_by VARCHAR(64),
    resolved_at TIMESTAMP,
    resolution_note TEXT,
    created_at TIMESTAMP NOT NULL
);
```

### 9.6 AuditLog (감사 로그)

```sql
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(32) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    previous_state VARCHAR(20),
    new_state VARCHAR(20),
    previous_data JSONB,
    new_data JSONB,
    reason TEXT,
    actor VARCHAR(64),
    actor_ip VARCHAR(45),
    created_at TIMESTAMP NOT NULL,

    INDEX idx_entity (entity_type, entity_id),
    INDEX idx_created_at (created_at)
);

-- 감사 로그 파티셔닝 (월별)
CREATE TABLE audit_logs_2024_12 PARTITION OF audit_logs
    FOR VALUES FROM ('2024-12-01') TO ('2025-01-01');
```

---

## 10. 테스트 전략

### 10.1 테스트 시나리오

| 시나리오 | 검증 포인트 | 우선순위 |
|---------|-----------|---------|
| 중복 결제 방지 | 동일 Idempotency-Key로 100회 요청 시 1건만 생성 | P0 |
| 동시성 처리 | 같은 주문에 대해 10개 스레드 동시 결제 시 1건만 성공 | P0 |
| 상태 전이 검증 | 불가능한 상태 전이 시도 시 예외 발생 | P0 |
| 청산 정확성 | 1000건 결제 후 청산 금액 = 개별 금액 합계 | P0 |
| 수수료 계산 | 다양한 수수료율, 경계값에서 정확한 계산 | P0 |
| 정산 재시도 | Mock Bank 실패 후 복구 시 자동 재시도하여 완료 | P1 |
| 부하 테스트 | 100 TPS 부하에서 에러율 < 1%, 응답시간 p99 < 500ms | P1 |
| 보안 테스트 | SQL Injection, XSS 등 OWASP Top 10 방어 | P1 |

### 10.2 테스트 커버리지 목표

| 계층 | 목표 | 필수 항목 |
|------|------|----------|
| 도메인 로직 | 90% | PaymentService, ClearingService, FeeCalculator |
| 상태 머신 | 100% | 모든 상태 전이 경로 |
| Repository | 80% | 커스텀 쿼리 |
| Controller | 70% | 입력 검증, 에러 처리 |
| **전체** | **80%** | - |

### 10.3 테스트 도구

| 용도 | 도구 |
|------|------|
| 단위 테스트 | JUnit 5, Mockito, AssertJ |
| 통합 테스트 | Testcontainers (PostgreSQL, Redis) |
| API 테스트 | WireMock, RestAssured |
| 성능 테스트 | Gatling, JMeter |
| 보안 테스트 | OWASP ZAP |

---

## 11. 구현 로드맵

| Phase | 기간 | 목표 |
|-------|------|------|
| **1** | Week 1-2 | 프로젝트 셋업, 기본 도메인 모델, Payment CRUD, 상태 머신 |
| **2** | Week 3-4 | Mock PG 구현, 멱등성/동시성 처리, 감사 로깅, 수수료 계산 |
| **3** | Week 5-6 | Clearing/Settlement 서비스, 배치 처리, 역정산 |
| **4** | Week 7-8 | 정산 대사, 관리자 대시보드 (React), 테스트 시나리오 구현 |
| **5** | Week 9-10 | 부하 테스트, 보안 테스트, 문서화, 최종 정리 |

---

## 12. 성공 지표

### 12.1 기능 완성도

- [ ] Payment → Clearing → Settlement 전체 흐름 정상 동작
- [ ] 역정산 (환불) 프로세스 정상 동작
- [ ] 정산 대사 프로세스 정상 동작

### 12.2 품질 지표

- [ ] 트랜잭션 안정성: 동시 요청 시 데이터 정합성 100% 유지
- [ ] 멱등성 검증: 중복 요청 시 결제 중복 생성 0건
- [ ] 테스트 커버리지: 핵심 비즈니스 로직 80% 이상
- [ ] 성능: 100 TPS, p99 < 500ms, 에러율 < 1%

### 12.3 문서화

- [ ] API 문서 (Swagger/OpenAPI)
- [ ] 아키텍처 다이어그램
- [ ] 데이터 모델 ERD
- [ ] README 및 설치 가이드

### 12.4 기술 문서화

- [ ] 아키텍처 결정 사유 (ADR) 문서화
- [ ] 트러블슈팅 경험 정리
- [ ] 주요 기술 선택 근거 문서화

---

**— End of Document —**
