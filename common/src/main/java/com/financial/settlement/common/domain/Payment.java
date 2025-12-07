package com.financial.settlement.common.domain;

import com.financial.settlement.common.audit.AuditEventListener;
import com.financial.settlement.common.exception.BusinessException;
import com.financial.settlement.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 결제 엔티티
 */
@Entity
@Table(name = "payments")
@EntityListeners(AuditEventListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency = "KRW";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "pg_transaction_id", length = 64)
    private String pgTransactionId;

    @Column(name = "approval_number", length = 20)
    private String approvalNumber;

    @Column(name = "card_bin", length = 6)
    private String cardBin;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "customer_name", length = 50)
    private String customerName;

    @Column(name = "customer_email", length = 100)
    private String customerEmail;

    @Column(name = "fee_amount", precision = 15, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "vat_amount", precision = 15, scale = 2)
    private BigDecimal vatAmount;

    @Column(name = "net_amount", precision = 15, scale = 2)
    private BigDecimal netAmount;

    @Version
    @Column(name = "version")
    private Long version = 0L;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Builder
    public Payment(String idempotencyKey, Merchant merchant, String orderId,
                   BigDecimal amount, String currency, PaymentMethod paymentMethod,
                   String cardBin, String cardLast4, String customerName, String customerEmail) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.merchant = merchant;
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency != null ? currency : "KRW";
        this.paymentMethod = paymentMethod;
        this.cardBin = cardBin;
        this.cardLast4 = cardLast4;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.status = PaymentStatus.REQUESTED;
    }

    /**
     * 상태 전이 (검증 포함)
     */
    public void transitionTo(PaymentStatus newStatus) {
        try {
            this.status.validateTransition(newStatus);
            this.status = newStatus;
        } catch (PaymentStatus.InvalidStateTransitionException e) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE_TRANSITION,
                    String.format("Cannot transition from %s to %s", e.getFromStatus(), e.getToStatus()));
        }
    }

    /**
     * 처리 중 상태로 전이
     */
    public void startProcessing() {
        transitionTo(PaymentStatus.PROCESSING);
    }

    /**
     * 승인 처리
     */
    public void approve(String pgTransactionId, String approvalNumber) {
        transitionTo(PaymentStatus.APPROVED);
        this.pgTransactionId = pgTransactionId;
        this.approvalNumber = approvalNumber;
        this.approvedAt = LocalDateTime.now();
    }

    /**
     * 실패 처리
     */
    public void fail() {
        transitionTo(PaymentStatus.FAILED);
    }

    /**
     * 취소 처리
     */
    public void cancel() {
        if (!status.isCancellable()) {
            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED,
                    "Current status: " + status.name());
        }
        transitionTo(PaymentStatus.CANCELLED);
        this.cancelledAt = LocalDateTime.now();
    }

    /**
     * 청산 처리
     */
    public void clear() {
        transitionTo(PaymentStatus.CLEARED);
    }

    /**
     * 정산 처리
     */
    public void settle() {
        transitionTo(PaymentStatus.SETTLED);
    }

    /**
     * 환불 처리
     */
    public void refund() {
        if (!status.isRefundable()) {
            throw new BusinessException(ErrorCode.PAYMENT_REFUND_NOT_ALLOWED,
                    "Current status: " + status.name());
        }
        transitionTo(PaymentStatus.REFUNDED);
    }

    /**
     * 수수료 정보 설정
     */
    public void setFeeInfo(BigDecimal feeAmount, BigDecimal vatAmount, BigDecimal netAmount) {
        this.feeAmount = feeAmount;
        this.vatAmount = vatAmount;
        this.netAmount = netAmount;
    }

    /**
     * 카드 정보가 마스킹된 형태로 반환
     */
    public String getMaskedCardNumber() {
        if (cardBin == null || cardLast4 == null) {
            return null;
        }
        return cardBin + "******" + cardLast4;
    }
}
