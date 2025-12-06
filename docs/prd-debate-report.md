# PayFlow PRD 토론 분석 보고서

> **분석일**: 2024년 12월
> **분석 대상**: PayFlow PRD v1.0
> **분석 방법**: 4개 전문가 관점에서 다각도 분석

---

## 목차

1. [종합 요약](#1-종합-요약)
2. [기술 아키텍처 관점](#2-기술-아키텍처-관점)
3. [금융 도메인 관점](#3-금융-도메인-관점)
4. [테스트/품질 관점](#4-테스트품질-관점)
5. [보안/컴플라이언스 관점](#5-보안컴플라이언스-관점)
6. [종합 개선 권장사항](#6-종합-개선-권장사항)
7. [부록: 체크리스트](#7-부록-체크리스트)

---

## 1. 종합 요약

### 1.1 PRD 품질 점수

| 관점 | 점수 | 평가 |
|------|------|------|
| 기술 아키텍처 | 75/100 | 핵심 개념 우수, 분산 트랜잭션 상세화 필요 |
| 금융 도메인 | 75/100 | 기본 프로세스 정확, 수수료/대사 누락 |
| 테스트/품질 | 70/100 | 핵심 시나리오 포함, 엣지케이스 부족 |
| 보안/컴플라이언스 | 40/100 | 기본 요소만 있음, PCI-DSS 미흡 |
| **종합** | **65/100** | 학습 목적으로 우수, 실무 적용 시 보완 필요 |

### 1.2 핵심 강점

- ✅ Payment → Clearing → Settlement 3단계 프로세스 정확히 모델링
- ✅ 멱등성, 분산락, 낙관적락 등 동시성 제어 전략 포함
- ✅ 상태 머신 설계가 금융권 실무와 일치
- ✅ Mock 환경으로 다양한 시나리오 테스트 가능

### 1.3 핵심 개선 필요 사항

| 우선순위 | 항목 | 영향도 |
|---------|------|--------|
| **P0** | 수수료 계산 로직 추가 | CRITICAL |
| **P0** | 정산 대사(Reconciliation) 프로세스 | CRITICAL |
| **P0** | 역정산/보상 트랜잭션 처리 | CRITICAL |
| **P1** | PCI-DSS 보안 요구사항 명확화 | HIGH |
| **P1** | 상태 전이 테스트 케이스 추가 | HIGH |
| **P2** | FDS(이상거래탐지) 기본 룰 | MEDIUM |

---

## 2. 기술 아키텍처 관점

### 2.1 강점 분석

#### 도메인 모델링 우수
```
REQUESTED → PROCESSING → APPROVED → CLEARED → SETTLED
                ↓
            FAILED / CANCELLED
```
- 금융권 실무와 일치하는 상태 흐름
- 각 단계가 독립적 트랜잭션으로 장애 격리 가능

#### 동시성 제어 전략 (3중 방어)
```
L1: API Gateway - Idempotency-Key 헤더 검증
L2: Application - Redis 분산락으로 동시 처리 차단
L3: Database - JPA @Version 낙관적락 + Unique Constraint
```

### 2.2 잠재적 문제점

#### 분산 트랜잭션 처리 모호함
```
문제: PG 승인 성공 후 DB 업데이트 실패 시 보상 전략 없음
해결: Saga 패턴 또는 Outbox 패턴 도입 필요
```

#### Redis 분산락 구현 디테일 누락
```
누락된 고려사항:
- Lock Timeout 설정값
- Lock Release 실패 시 데드락 방지
- Redis 장애 시 Fallback 전략
```

#### 배치 청산 대용량 처리 전략 부재
```
현재: "매일 자정 APPROVED → CLEARED 변경"
문제: 100만 건 처리 시 Memory Overflow 위험
해결: Chunk 단위 처리 + Cursor 기반 페이징
```

### 2.3 개선 제안

#### Event Sourcing 도입
```java
// 현재: 상태만 저장
Payment { status: APPROVED }

// 개선: 모든 이벤트 저장 (완벽한 감사 추적)
PaymentCreatedEvent  { amount: 10000 }
PaymentApprovedEvent { pgTxnId: "PG123" }
PaymentClearedEvent  { clearingId: "CLR456" }
```

#### Circuit Breaker 패턴 적용
```java
@CircuitBreaker(name = "mockPG", fallbackMethod = "approveFallback")
public PGResponse approve(PaymentRequest req) {
    return pgClient.approve(req);
}

// PG 장애 시 임시 승인 후 정산 시 정합성 검증
public PGResponse approveFallback(PaymentRequest req, Exception e) {
    return PGResponse.pendingApproval("TEMP-" + UUID.randomUUID());
}
```

---

## 3. 금융 도메인 관점

### 3.1 현실성 평가

| 항목 | 실제 유사도 | 평가 |
|------|-----------|------|
| 3단계 프로세스 | 90% | 실제 PG사와 동일 구조 |
| 상태 머신 | 85% | 현실적, 보상 트랜잭션 누락 |
| Mock 시나리오 | 60% | 너무 단순 (실제 30+ 오류 코드) |
| 은행 점검시간 | 50% | 주말 점검 등 미고려 |

### 3.2 누락된 필수 요소

#### 🚨 수수료 체계 (CRITICAL)
```sql
-- 추가 필요한 테이블
CREATE TABLE merchant_fee_policies (
    merchant_id VARCHAR(32) PRIMARY KEY,
    payment_method VARCHAR(20),        -- 카드/계좌이체 등
    fee_rate DECIMAL(5,4),             -- 수수료율 (예: 0.0350 = 3.5%)
    fixed_fee DECIMAL(10,2),           -- 건당 고정 수수료
    vat_inclusive BOOLEAN              -- VAT 포함 여부
);

CREATE TABLE settlement_details (
    payment_id UUID,
    gross_amount DECIMAL(15,2),        -- 총 결제금액
    fee_amount DECIMAL(15,2),          -- 수수료
    vat_amount DECIMAL(15,2),          -- 부가세
    net_amount DECIMAL(15,2)           -- 실정산액
);
```

#### 🚨 역정산 프로세스 (CRITICAL)
```
시나리오:
Day 1, 10:00 - 고객 10만원 결제 (APPROVED)
Day 2, 09:00 - 정산 완료 (SETTLED) - 가맹점 입금됨
Day 2, 15:00 - 고객 환불 요청 ← 이미 정산 완료!

처리:
1. 환불 요청 접수 → REFUND_REQUESTED
2. 가맹점 계좌에서 해당 금액 회수
3. 고객 카드로 환불 승인
4. REFUNDED 상태로 변경
```

#### 🚨 정산 대사(Reconciliation) (CRITICAL)
```java
@Scheduled(cron = "0 0 9 * * *")  // 매일 오전 9시
public void dailyReconciliation() {
    // 1. 우리 시스템 집계
    BigDecimal ourTotal = paymentRepository.sumByDate(yesterday);

    // 2. 은행 파일 파싱
    BigDecimal bankTotal = bankService.downloadStatement(yesterday).getTotal();

    // 3. 불일치 시 알림
    if (!ourTotal.equals(bankTotal)) {
        alertService.sendReconciliationAlert("금액 불일치: " + diff);
    }
}
```

#### 정산 주기 다양성
```java
enum SettlementCycle {
    D_PLUS_1,   // 익일 정산 (일반)
    D_PLUS_3,   // D+3 정산 (소호 가맹점)
    D_PLUS_7,   // D+7 정산 (신규/고위험 가맹점)
    WEEKLY,     // 주간 정산
    MONTHLY     // 월간 정산
}
```

### 3.3 Mock 시나리오 개선

#### 현재 (너무 단순)
```
금액 끝자리 01 → 잔액부족
금액 끝자리 02 → 카드만료
금액 끝자리 99 → 타임아웃
```

#### 개선안 (카드번호 기반)
```java
switch (cardNumber) {
    case "1111-1111-1111-1111": return error("INSUFFICIENT_BALANCE");
    case "2222-2222-2222-2222": return error("EXPIRED_CARD");
    case "3333-3333-3333-3333": return error("STOLEN_CARD");
    case "4444-4444-4444-4444": return error("INVALID_CVV");
    case "5555-5555-5555-5555": return redirect3DSecure();
    case "9999-9999-9999-9999": return timeout(6000);
    default: return success();
}
```

---

## 4. 테스트/품질 관점

### 4.1 현재 테스트 시나리오 평가

| 시나리오 | 충분성 | 리스크 |
|---------|--------|--------|
| 중복 결제 방지 (100회 → 1건) | ⚠️ 부분적 | HIGH |
| 동시성 처리 (10스레드 → 1건) | ⚠️ 부분적 | CRITICAL |
| 청산 정확성 (1000건 합계) | ✅ 적절 | CRITICAL |
| 정산 재시도 | ⚠️ 부분적 | HIGH |
| 부하 테스트 (100 TPS) | ✅ 적절 | MEDIUM |

### 4.2 누락된 테스트 케이스

#### 상태 전이 검증 (CRITICAL)
```java
@ParameterizedTest
@CsvSource({
    "SETTLED, PROCESSING, 정산 완료 건은 처리중으로 변경 불가",
    "CLEARED, REQUESTED, 청산된 건은 요청 상태로 복귀 불가",
    "CANCELLED, APPROVED, 취소된 건은 승인 불가"
})
void testInvalidStateTransitions(PaymentStatus from, PaymentStatus to) {
    // 비즈니스 규칙 위배 상태 전이 차단 검증
}
```

#### 금액 경계값 테스트 (CRITICAL)
```java
@Test
void testAmountBoundaries() {
    // 0원 결제 → 유효성 검증 실패
    // 음수 금액 → 비즈니스 규칙 위반
    // DECIMAL(15,2) 한계값 → 999,999,999,999.99
    // 소수점 3자리 → 반올림 규칙 적용
}
```

#### 부동소수점 오류 방지 (CRITICAL)
```java
@Test
void testDecimalPrecision() {
    List<BigDecimal> amounts = List.of(
        new BigDecimal("10.01"),
        new BigDecimal("20.02"),
        new BigDecimal("30.03")
    );

    ClearingResult result = clearingService.clear(amounts);

    // Float 사용 시 60.05999...가 될 수 있음
    assertThat(result.getTotal()).isEqualByComparingTo("60.06");
}
```

### 4.3 테스트 아키텍처 권장 구조

```
backend/src/test/java/
├── integration/          # 통합 테스트
│   ├── payment/
│   │   ├── IdempotencyIntegrationTest.java
│   │   └── ConcurrencyIntegrationTest.java
│   ├── clearing/
│   │   └── ClearingAccuracyTest.java
│   └── settlement/
│       └── SettlementRetryTest.java
├── unit/                 # 단위 테스트
│   ├── domain/
│   │   └── PaymentStateMachineTest.java
│   └── service/
│       └── FeeCalculationTest.java
├── performance/          # 성능 테스트
│   └── PaymentLoadTest.java
└── contract/             # 계약 테스트
    └── MockPGContractTest.java
```

### 4.4 커버리지 목표

| 계층 | 목표 | 우선순위 |
|------|------|---------|
| 도메인 로직 | 90% | CRITICAL |
| 상태 머신 | 100% | CRITICAL |
| Repository | 80% | HIGH |
| Controller | 70% | MEDIUM |
| **전체 평균** | **80%** | - |

---

## 5. 보안/컴플라이언스 관점

### 5.1 현재 보안 설계 평가

#### 긍정적 요소
- ✅ 카드번호 마스킹 저장
- ✅ Audit Log Append-only 정책
- ✅ JWT 기반 API 인증
- ✅ Rate Limiting (100 req/sec)

#### 불충분한 요소
- ⚠️ JWT 만료 시간/갱신 정책 미명시
- ⚠️ TLS 사용 명시 없음
- ⚠️ 접근 제어(RBAC) 부재
- ⚠️ 입력 검증 상세 규칙 없음

### 5.2 PCI-DSS 준수 현황

| 요구사항 | 준수 여부 | 비고 |
|---------|----------|------|
| 1. 방화벽 구성 | ❌ 미명시 | 네트워크 구성도 없음 |
| 3. 저장 카드 데이터 보호 | ⚠️ 부분 | 마스킹만 언급, 암호화 미흡 |
| 4. 전송 데이터 암호화 | ❌ 미명시 | TLS 사용 명시 없음 |
| 7. 접근 제한 | ❌ 불충분 | RBAC 부재 |
| 10. 접근 로그 추적 | ✅ 양호 | Audit Log 구현 |

### 5.3 즉시 적용 필수 사항 (P0)

#### 민감정보 암호화
```yaml
저장 데이터:
  카드번호: AES-256-GCM 암호화
  마스킹 형식: "1234-56**-****-7890"
  CVV: 저장 절대 금지 (PCI-DSS 위반)

전송 암호화:
  TLS 1.3 강제 사용
  HTTP → HTTPS 자동 리다이렉트
```

#### 접근 제어 (RBAC)
```java
enum Role {
    SYSTEM_ADMIN,      // 전체 시스템 관리
    MERCHANT_ADMIN,    // 자사 가맹점 관리
    MERCHANT_VIEWER,   // 자사 가맹점 조회만
    AUDITOR            // 감사 로그 조회 전용
}

// 가맹점 간 데이터 격리 필수
@PreAuthorize("hasRole('MERCHANT_ADMIN')")
public List<Payment> getPayments(@AuthenticationPrincipal MerchantUser user) {
    return paymentService.findByMerchantId(user.getMerchantId());
}
```

#### 입력 검증 강화
```java
public class PaymentRequest {
    @NotNull
    @Pattern(regexp = "^[a-zA-Z0-9-]{16,64}$")
    private String idempotencyKey;

    @NotNull
    @DecimalMin("100")        // 최소 100원
    @DecimalMax("10000000")   // 최대 1천만원
    private BigDecimal amount;
}
```

### 5.4 보안 강화 우선순위

| 우선순위 | 항목 | 적용 시점 |
|---------|------|----------|
| P0 | 민감정보 암호화 (AES-256) | 런칭 전 필수 |
| P0 | RBAC 구현 | 런칭 전 필수 |
| P0 | 입력 검증 강화 | 런칭 전 필수 |
| P1 | JWT 보안 강화 (15분 만료) | 1개월 내 |
| P1 | 보안 모니터링 구축 | 1개월 내 |
| P2 | 침투 테스트 | 3개월 내 |
| P3 | 2FA 적용 | 장기 과제 |

---

## 6. 종합 개선 권장사항

### 6.1 PRD 보완 우선순위

#### Phase 0: PRD 보완 (Week 0)
```markdown
- [ ] 수수료 테이블 설계 추가
- [ ] 역정산 프로세스 정의
- [ ] 정산 대사 프로세스 정의
- [ ] FDS 기본 룰 3가지 정의
- [ ] Mock 시나리오 카드번호 체계로 변경
- [ ] PCI-DSS 보안 요구사항 명확화
```

#### Phase 1-2 수정 (Week 1-4)
```markdown
기존: 프로젝트 셋업, Payment CRUD, Mock PG
추가:
- [ ] 수수료 계산 엔진
- [ ] 상태 전이 테스트 (100% 커버리지)
- [ ] 금액 경계값 테스트
```

#### Phase 3-4 수정 (Week 5-8)
```markdown
기존: Clearing/Settlement, 대시보드
추가:
- [ ] 정산 대사 프로세스
- [ ] 역정산 처리
- [ ] 보안 모니터링
```

### 6.2 아키텍처 개선 권장

```
현재 구조:
[Client] → [API Gateway] → [Payment Service] → [Mock PG]
                                    ↓
                          [Clearing Service]
                                    ↓
                          [Settlement Service] → [Mock Bank]

개선 구조:
[Client] → [API Gateway] → [Payment Service] → [Mock PG]
                ↓                   ↓
         [Rate Limiter]    [Event Publisher]
                                    ↓
                            [Event Store]
                                    ↓
                    ┌───────────────┴───────────────┐
                    ↓                               ↓
           [Clearing Service]              [Audit Service]
                    ↓                               ↓
           [Settlement Service] ←──────── [Reconciliation]
                    ↓
              [Mock Bank]
```

---

## 7. 부록: 체크리스트

### 프로덕션 배포 전 필수 점검

```markdown
[ ] 1. 암호화
    [ ] TLS 1.3 적용
    [ ] 카드번호 AES-256 암호화
    [ ] CVV 저장 안 함 확인

[ ] 2. 인증/인가
    [ ] JWT 만료 시간 설정 (15분)
    [ ] RBAC 역할 기반 접근 제어
    [ ] 가맹점 간 데이터 격리

[ ] 3. 입력 검증
    [ ] 모든 입력 필드 유효성 검사
    [ ] SQL Injection 테스트 통과

[ ] 4. 테스트
    [ ] 상태 전이 100% 커버리지
    [ ] 동시성 테스트 통과
    [ ] 부하 테스트 (100 TPS) 통과

[ ] 5. 모니터링
    [ ] 보안 이벤트 실시간 알림
    [ ] 감사 로그 외부 저장

[ ] 6. 문서화
    [ ] API 문서 완비
    [ ] 아키텍처 다이어그램
    [ ] 보안 정책서
```

---

**작성일**: 2024년 12월
**분석 도구**: Claude AI Multi-Perspective Analysis
**다음 리뷰 예정**: Phase 1 완료 후
