# PayFlow 시스템 아키텍처

> 결제 → 청산 → 정산 전체 흐름

---

## 1. 시스템 구성도

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              PayFlow System                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────┐     ┌─────────────┐     ┌─────────────────┐     ┌──────────┐  │
│  │ Client  │────▶│ API Gateway │────▶│ Payment Service │────▶│ Mock PG  │  │
│  └─────────┘     └─────────────┘     └─────────────────┘     └──────────┘  │
│                        │                      │                             │
│                        ▼                      ▼                             │
│                 ┌────────────┐         ┌────────────┐                       │
│                 │   Redis    │         │ PostgreSQL │                       │
│                 │ (분산 락)   │         │  (ACID)    │                       │
│                 └────────────┘         └────────────┘                       │
│                                              │                              │
│                        ┌─────────────────────┴─────────────────────┐        │
│                        ▼                                           ▼        │
│                ┌───────────────┐                          ┌───────────────┐ │
│                │   Clearing    │◀── Scheduler (자정) ──▶  │    Audit      │ │
│                │   Service     │                          │   Service     │ │
│                └───────────────┘                          └───────────────┘ │
│                        │                                                    │
│                        ▼                                                    │
│                ┌───────────────┐     ┌────────────┐                         │
│                │  Settlement   │────▶│ Mock Bank  │                         │
│                │   Service     │     └────────────┘                         │
│                └───────────────┘                                            │
│                        │                                                    │
│                        ▼                                                    │
│                ┌───────────────┐                                            │
│                │Reconciliation │                                            │
│                │   Service     │                                            │
│                └───────────────┘                                            │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. 결제 상태 흐름 (State Machine)

```mermaid
stateDiagram-v2
    [*] --> REQUESTED: 결제 요청

    REQUESTED --> PROCESSING: PG 승인 요청

    PROCESSING --> APPROVED: 승인 완료
    PROCESSING --> FAILED: 승인 실패

    APPROVED --> CLEARED: 청산 배치
    APPROVED --> CANCELLED: 취소 요청

    CLEARED --> SETTLED: 정산 완료

    SETTLED --> REFUNDED: 환불 처리

    FAILED --> [*]
    CANCELLED --> [*]
    REFUNDED --> [*]
```

---

## 3. 결제 처리 시퀀스 (Payment Flow)

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant API as API Server
    participant PS as PaymentService
    participant R as Redis
    participant DB as PostgreSQL
    participant PG as Mock PG

    C->>API: POST /payments<br/>Idempotency-Key: xxx

    API->>PS: 결제 요청 처리

    PS->>R: GET idempotency:xxx
    R-->>PS: (없음)

    PS->>R: LOCK payment:order:123
    R-->>PS: OK (락 획득)

    PS->>DB: Payment 생성 (REQUESTED)
    DB-->>PS: OK

    PS->>DB: 상태 변경 (PROCESSING)

    PS->>PG: 승인 요청
    PG-->>PS: APPROVED

    PS->>DB: 상태 변경 (APPROVED)<br/>수수료 계산 저장

    PS->>R: SET idempotency:xxx (TTL 24h)
    R-->>PS: OK

    PS->>R: UNLOCK payment:order:123
    R-->>PS: OK

    PS-->>API: PaymentResponse
    API-->>C: 200 OK
```

---

## 4. 청산/정산 배치 시퀀스 (Clearing & Settlement)

```mermaid
sequenceDiagram
    autonumber
    participant SCH as Scheduler
    participant CS as ClearingService
    participant SS as SettlementService
    participant DB as PostgreSQL
    participant BANK as Mock Bank

    Note over SCH: 매일 자정 실행

    SCH->>CS: 청산 배치 시작

    CS->>DB: APPROVED 건 조회<br/>(청산 대상)
    DB-->>CS: Payment 목록

    loop 가맹점별 처리
        CS->>CS: 거래 집계
        CS->>CS: 수수료 계산
        CS->>DB: Payment 상태 변경<br/>(APPROVED → CLEARED)
        CS->>DB: Settlement 레코드 생성
    end

    CS->>SS: 정산 실행 요청

    SS->>DB: CLEARED 건 조회
    DB-->>SS: Settlement 목록

    loop 정산 건별 처리
        SS->>BANK: 이체 요청
        alt 이체 성공
            BANK-->>SS: 이체 완료
            SS->>DB: 상태 변경 (SETTLED)
        else 이체 실패
            BANK-->>SS: 에러
            SS->>SS: 재시도 (Exponential Backoff)
        end
    end

    SS-->>CS: 정산 완료
    CS-->>SCH: 배치 완료
```

---

## 5. 정산 대사 시퀀스 (Reconciliation)

```mermaid
sequenceDiagram
    autonumber
    participant SCH as Scheduler
    participant RS as ReconciliationService
    participant DB as PostgreSQL
    participant BANK as Mock Bank
    participant ADMIN as Admin

    Note over SCH: 매일 오전 실행

    SCH->>RS: 대사 시작

    RS->>DB: 우리 정산 금액 조회
    DB-->>RS: 정산 합계

    RS->>BANK: 은행 입금 내역 조회
    BANK-->>RS: 입금 합계

    RS->>RS: 금액 비교

    alt 금액 일치
        RS->>DB: 상태 저장 (MATCHED)
    else 금액 불일치
        RS->>DB: 상태 저장 (MISMATCHED)
        RS->>ADMIN: 불일치 알림

        ADMIN->>RS: 수동 조정 요청
        RS->>DB: 상태 변경 (RESOLVED)
    end

    RS-->>SCH: 대사 완료
```

---

## 6. 역정산 시퀀스 (Refund after Settlement)

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant API as API Server
    participant RS as RefundService
    participant DB as PostgreSQL
    participant BANK as Mock Bank

    C->>API: POST /payments/{id}/refund

    API->>RS: 환불 요청

    RS->>DB: 결제 상태 확인
    DB-->>RS: SETTLED 확인

    RS->>DB: 가맹점 잔액 확인
    DB-->>RS: 잔액 정보

    alt 잔액 충분
        RS->>BANK: 가맹점 계좌 출금
        BANK-->>RS: 출금 완료

        RS->>BANK: 고객 계좌 입금
        BANK-->>RS: 입금 완료

        RS->>DB: 상태 변경 (REFUNDED)
    else 잔액 부족
        RS->>DB: 상태 변경 (REFUND_PENDING)
        Note over RS,DB: 다음 정산에서 차감
    end

    RS-->>API: RefundResponse
    API-->>C: 200 OK
```

---

## 7. 멱등성 처리 흐름

```mermaid
flowchart TD
    A[요청 수신<br/>Idempotency-Key: xxx] --> B{Redis에서<br/>키 조회}

    B -->|키 없음| C[분산 락 획득]
    C --> D[결제 처리]
    D --> E[결과 캐시 저장<br/>TTL: 24h]
    E --> F[응답 반환]

    B -->|키 존재<br/>처리 완료| G[캐시된 결과 반환]

    B -->|키 존재<br/>처리 중| H[202 Accepted<br/>Retry-After]

    B -->|키 존재<br/>금액 불일치| I[409 Conflict]
```

---

## 8. 동시성 제어 전략

```mermaid
flowchart LR
    subgraph 분산락["1. 분산 락 (Redis)"]
        A1[Server A] --> R1[Redis Lock]
        A2[Server B] --> R1
        R1 --> A1_OK[획득 성공]
        R1 -.-> A2_WAIT[대기/재시도]
    end

    subgraph 낙관적["2. 낙관적 락 (JPA)"]
        B1["@Version 필드"] --> B2[커밋 시점 검증]
        B2 --> B3[충돌 시 예외]
    end

    subgraph 비관적["3. 비관적 락 (DB)"]
        C1[SELECT FOR UPDATE] --> C2[행 잠금]
        C2 --> C3[트랜잭션 완료까지 유지]
    end
```

---

## 9. 수수료 계산 흐름

```mermaid
flowchart LR
    A[결제금액<br/>100,000원] --> B[수수료 계산<br/>× 3.3%]
    B --> C[수수료<br/>3,300원]
    C --> D[VAT 계산<br/>× 10%]
    D --> E[VAT<br/>330원]

    A --> F[정산금 계산]
    C --> F
    E --> F
    F --> G[정산금<br/>96,370원]
```

---

## 10. ERD (Entity Relationship Diagram)

```mermaid
erDiagram
    MERCHANTS ||--o{ PAYMENTS : has
    MERCHANTS ||--o{ MERCHANT_FEE_POLICIES : has
    MERCHANTS ||--o{ SETTLEMENTS : has

    PAYMENTS ||--o{ PARTIAL_CANCELLATIONS : has
    PAYMENTS ||--o{ SETTLEMENT_DETAILS : included_in

    SETTLEMENTS ||--o{ SETTLEMENT_DETAILS : contains

    MERCHANTS {
        string id PK
        string name
        string business_number
        string settlement_cycle
        string status
    }

    MERCHANT_FEE_POLICIES {
        bigint id PK
        string merchant_id FK
        string payment_method
        decimal fee_rate
        date effective_from
    }

    PAYMENTS {
        uuid id PK
        string idempotency_key UK
        string merchant_id FK
        string order_id
        decimal amount
        string status
        decimal fee_amount
        decimal net_amount
    }

    PARTIAL_CANCELLATIONS {
        bigint id PK
        uuid payment_id FK
        decimal cancel_amount
        decimal remaining_amount
    }

    SETTLEMENTS {
        uuid id PK
        string merchant_id FK
        date settlement_date
        decimal total_amount
        decimal net_amount
        string status
    }

    SETTLEMENT_DETAILS {
        bigint id PK
        uuid settlement_id FK
        uuid payment_id FK
        decimal gross_amount
        decimal fee_amount
        decimal net_amount
    }

    RECONCILIATIONS {
        uuid id PK
        date reconciliation_date
        decimal our_total
        decimal bank_total
        decimal difference
        string status
    }

    AUDIT_LOGS {
        bigint id PK
        string entity_type
        string entity_id
        string action
        string previous_state
        string new_state
        string actor
    }
```

---

**— End of Document —**
