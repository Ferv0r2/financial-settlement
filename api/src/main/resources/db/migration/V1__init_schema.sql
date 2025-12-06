-- PayFlow 초기 스키마
-- V1: 기본 테이블 생성

-- UUID 확장 활성화
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =============================================
-- Merchant (가맹점)
-- =============================================
CREATE TABLE merchants (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    business_number VARCHAR(20) NOT NULL UNIQUE,
    email VARCHAR(100),
    phone VARCHAR(20),
    bank_code VARCHAR(10),
    account_number VARCHAR(30),
    account_holder VARCHAR(50),
    settlement_cycle VARCHAR(20) NOT NULL DEFAULT 'D3',  -- D1, D3, D7, WEEKLY, MONTHLY
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',        -- ACTIVE, INACTIVE, SUSPENDED
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_merchants_status ON merchants(status);
CREATE INDEX idx_merchants_business_number ON merchants(business_number);

-- =============================================
-- Merchant Fee Policy (가맹점 수수료 정책)
-- =============================================
CREATE TABLE merchant_fee_policies (
    id BIGSERIAL PRIMARY KEY,
    merchant_id VARCHAR(32) NOT NULL REFERENCES merchants(id),
    payment_method VARCHAR(20) NOT NULL,  -- CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, VIRTUAL_ACCOUNT
    fee_rate DECIMAL(5,4) NOT NULL,       -- 0.0330 = 3.3%
    fixed_fee DECIMAL(10,2) DEFAULT 0,    -- 건당 고정 수수료
    min_fee DECIMAL(10,2),                -- 최소 수수료
    max_fee DECIMAL(10,2),                -- 최대 수수료
    vat_inclusive BOOLEAN DEFAULT FALSE,  -- VAT 포함 여부
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(merchant_id, payment_method, effective_from)
);

CREATE INDEX idx_fee_policies_merchant ON merchant_fee_policies(merchant_id);
CREATE INDEX idx_fee_policies_effective ON merchant_fee_policies(effective_from, effective_to);

-- =============================================
-- Payment (결제)
-- =============================================
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    idempotency_key VARCHAR(64) UNIQUE NOT NULL,
    merchant_id VARCHAR(32) NOT NULL REFERENCES merchants(id),
    order_id VARCHAR(64) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'KRW',
    status VARCHAR(20) NOT NULL,          -- REQUESTED, PROCESSING, APPROVED, CLEARED, SETTLED, FAILED, CANCELLED, REFUNDED
    payment_method VARCHAR(20) NOT NULL,  -- CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, VIRTUAL_ACCOUNT
    pg_transaction_id VARCHAR(64),
    approval_number VARCHAR(20),
    card_bin VARCHAR(6),                  -- 카드 앞 6자리 (BIN)
    card_last4 VARCHAR(4),                -- 카드 뒤 4자리
    customer_name VARCHAR(50),
    customer_email VARCHAR(100),
    fee_amount DECIMAL(15,2),
    vat_amount DECIMAL(15,2),
    net_amount DECIMAL(15,2),
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP,
    cancelled_at TIMESTAMP,

    CONSTRAINT chk_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_payments_merchant_status ON payments(merchant_id, status);
CREATE INDEX idx_payments_created_at ON payments(created_at);
CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_idempotency_key ON payments(idempotency_key);

-- =============================================
-- Partial Cancellation (부분 취소)
-- =============================================
CREATE TABLE partial_cancellations (
    id BIGSERIAL PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id),
    cancel_amount DECIMAL(15,2) NOT NULL,
    remaining_amount DECIMAL(15,2) NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_cancel_amount_positive CHECK (cancel_amount > 0)
);

CREATE INDEX idx_partial_cancellations_payment ON partial_cancellations(payment_id);

-- =============================================
-- Settlement (정산)
-- =============================================
CREATE TABLE settlements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    merchant_id VARCHAR(32) NOT NULL REFERENCES merchants(id),
    settlement_date DATE NOT NULL,
    total_amount DECIMAL(15,2) NOT NULL,
    total_fee DECIMAL(15,2) NOT NULL,
    total_vat DECIMAL(15,2) NOT NULL,
    net_amount DECIMAL(15,2) NOT NULL,
    payment_count INT NOT NULL,
    refund_count INT DEFAULT 0,
    refund_amount DECIMAL(15,2) DEFAULT 0,
    status VARCHAR(20) NOT NULL,          -- PENDING, PROCESSING, SETTLED, FAILED
    bank_transaction_id VARCHAR(64),
    settled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(merchant_id, settlement_date)
);

CREATE INDEX idx_settlements_status ON settlements(status);
CREATE INDEX idx_settlements_date ON settlements(settlement_date);

-- =============================================
-- Settlement Detail (정산 상세)
-- =============================================
CREATE TABLE settlement_details (
    id BIGSERIAL PRIMARY KEY,
    settlement_id UUID NOT NULL REFERENCES settlements(id),
    payment_id UUID NOT NULL REFERENCES payments(id),
    gross_amount DECIMAL(15,2) NOT NULL,
    fee_amount DECIMAL(15,2) NOT NULL,
    vat_amount DECIMAL(15,2) NOT NULL,
    net_amount DECIMAL(15,2) NOT NULL,
    is_refund BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_settlement_details_settlement ON settlement_details(settlement_id);
CREATE INDEX idx_settlement_details_payment ON settlement_details(payment_id);

-- =============================================
-- Reconciliation (대사)
-- =============================================
CREATE TABLE reconciliations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reconciliation_date DATE NOT NULL,
    our_total DECIMAL(15,2) NOT NULL,
    bank_total DECIMAL(15,2) NOT NULL,
    difference DECIMAL(15,2) NOT NULL,
    status VARCHAR(20) NOT NULL,          -- MATCHED, MISMATCHED, RESOLVED
    resolved_by VARCHAR(64),
    resolved_at TIMESTAMP,
    resolution_note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(reconciliation_date)
);

CREATE INDEX idx_reconciliations_status ON reconciliations(status);
CREATE INDEX idx_reconciliations_date ON reconciliations(reconciliation_date);

-- =============================================
-- Audit Log (감사 로그)
-- =============================================
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(32) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    previous_state VARCHAR(20),
    new_state VARCHAR(20),
    previous_data JSONB,
    new_data JSONB,
    reason TEXT,
    actor VARCHAR(64),
    actor_ip VARCHAR(45),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);

-- =============================================
-- 초기 테스트 데이터
-- =============================================
INSERT INTO merchants (id, name, business_number, email, settlement_cycle, status) VALUES
('MERCHANT_001', '테스트 가맹점 1', '123-45-67890', 'merchant1@example.com', 'D3', 'ACTIVE'),
('MERCHANT_002', '테스트 가맹점 2', '234-56-78901', 'merchant2@example.com', 'D1', 'ACTIVE');

INSERT INTO merchant_fee_policies (merchant_id, payment_method, fee_rate, effective_from) VALUES
('MERCHANT_001', 'CREDIT_CARD', 0.0330, '2024-01-01'),
('MERCHANT_001', 'DEBIT_CARD', 0.0150, '2024-01-01'),
('MERCHANT_002', 'CREDIT_CARD', 0.0300, '2024-01-01'),
('MERCHANT_002', 'DEBIT_CARD', 0.0130, '2024-01-01');
