package com.financial.settlement.common.domain;

/**
 * 가맹점 상태 Enum
 */
public enum MerchantStatus {

    ACTIVE("활성", "정상 운영 중"),
    INACTIVE("비활성", "휴면 상태"),
    SUSPENDED("정지", "계정 정지 상태");

    private final String displayName;
    private final String description;

    MerchantStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 결제 처리 가능 여부
     */
    public boolean canProcessPayments() {
        return this == ACTIVE;
    }
}
