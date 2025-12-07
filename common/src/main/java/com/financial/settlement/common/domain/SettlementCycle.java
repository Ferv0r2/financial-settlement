package com.financial.settlement.common.domain;

/**
 * 정산 주기 Enum
 */
public enum SettlementCycle {

    D1("익일 정산", 1),
    D3("D+3 정산", 3),
    D7("D+7 정산", 7),
    WEEKLY("주간 정산", 7),
    MONTHLY("월간 정산", 30);

    private final String displayName;
    private final int days;

    SettlementCycle(String displayName, int days) {
        this.displayName = displayName;
        this.days = days;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 정산까지 소요되는 일수
     */
    public int getDays() {
        return days;
    }
}
