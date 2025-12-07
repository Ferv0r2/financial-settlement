package com.financial.settlement.common.domain;

import java.math.BigDecimal;

/**
 * 결제 수단 Enum
 */
public enum PaymentMethod {

    CREDIT_CARD("신용카드", new BigDecimal("0.0330")),
    DEBIT_CARD("체크카드", new BigDecimal("0.0150")),
    BANK_TRANSFER("계좌이체", new BigDecimal("0.0100")),
    VIRTUAL_ACCOUNT("가상계좌", new BigDecimal("0.0100"));

    private final String displayName;
    private final BigDecimal defaultFeeRate;

    PaymentMethod(String displayName, BigDecimal defaultFeeRate) {
        this.displayName = displayName;
        this.defaultFeeRate = defaultFeeRate;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 기본 수수료율 반환 (가맹점별 정책이 없을 때 사용)
     */
    public BigDecimal getDefaultFeeRate() {
        return defaultFeeRate;
    }
}
