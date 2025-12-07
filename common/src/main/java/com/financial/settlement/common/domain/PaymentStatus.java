package com.financial.settlement.common.domain;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 결제 상태 Enum 및 상태 머신
 *
 * 상태 흐름:
 * REQUESTED → PROCESSING → APPROVED → CLEARED → SETTLED
 *         ↓           ↓          ↓
 *       FAILED      FAILED    CANCELLED
 *                               ↓
 *                            REFUNDED (SETTLED 상태에서만)
 */
public enum PaymentStatus {

    REQUESTED("요청됨", "결제 요청이 생성됨"),
    PROCESSING("처리중", "PG사에 결제 요청 중"),
    APPROVED("승인됨", "PG사 결제 승인 완료"),
    CLEARED("청산됨", "청산 처리 완료"),
    SETTLED("정산됨", "가맹점 정산 완료"),
    FAILED("실패", "결제 처리 실패"),
    CANCELLED("취소됨", "결제 취소 완료"),
    REFUNDED("환불됨", "정산 후 환불 완료");

    private final String displayName;
    private final String description;

    PaymentStatus(String displayName, String description) {
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
     * 현재 상태에서 전이 가능한 상태 목록 반환
     */
    public Set<PaymentStatus> getAllowedTransitions() {
        return switch (this) {
            case REQUESTED -> EnumSet.of(PROCESSING, FAILED);
            case PROCESSING -> EnumSet.of(APPROVED, FAILED);
            case APPROVED -> EnumSet.of(CLEARED, CANCELLED);
            case CLEARED -> EnumSet.of(SETTLED);
            case SETTLED -> EnumSet.of(REFUNDED);
            case FAILED, CANCELLED, REFUNDED -> Collections.emptySet();
        };
    }

    /**
     * 특정 상태로 전이 가능한지 확인
     */
    public boolean canTransitionTo(PaymentStatus targetStatus) {
        return getAllowedTransitions().contains(targetStatus);
    }

    /**
     * 상태 전이 검증 (불가능한 전이 시 예외 발생)
     */
    public void validateTransition(PaymentStatus targetStatus) {
        if (!canTransitionTo(targetStatus)) {
            throw new InvalidStateTransitionException(this, targetStatus);
        }
    }

    /**
     * 최종 상태(더 이상 전이 불가)인지 확인
     */
    public boolean isFinalState() {
        return getAllowedTransitions().isEmpty();
    }

    /**
     * 취소 가능한 상태인지 확인
     */
    public boolean isCancellable() {
        return this == APPROVED;
    }

    /**
     * 환불 가능한 상태인지 확인
     */
    public boolean isRefundable() {
        return this == SETTLED;
    }

    /**
     * 성공적인 결제 상태인지 확인
     */
    public boolean isSuccessful() {
        return this == APPROVED || this == CLEARED || this == SETTLED;
    }

    /**
     * 문자열로부터 PaymentStatus 변환 (대소문자 무시)
     */
    public static PaymentStatus fromString(String value) {
        return Arrays.stream(values())
                .filter(status -> status.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown payment status: " + value));
    }

    /**
     * 잘못된 상태 전이 예외
     */
    public static class InvalidStateTransitionException extends RuntimeException {
        private final PaymentStatus fromStatus;
        private final PaymentStatus toStatus;

        public InvalidStateTransitionException(PaymentStatus from, PaymentStatus to) {
            super(String.format("Cannot transition from %s to %s", from.name(), to.name()));
            this.fromStatus = from;
            this.toStatus = to;
        }

        public PaymentStatus getFromStatus() {
            return fromStatus;
        }

        public PaymentStatus getToStatus() {
            return toStatus;
        }
    }
}
