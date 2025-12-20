package com.financial.settlement.api.dto.payment;

import com.financial.settlement.common.domain.Payment;
import com.financial.settlement.common.domain.PaymentMethod;
import com.financial.settlement.common.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@Schema(description = "결제 응답")
public class PaymentResponse {

    @Schema(description = "결제 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "멱등성 키", example = "idem-20240115-001")
    private String idempotencyKey;

    @Schema(description = "가맹점 ID", example = "MERCHANT_001")
    private String merchantId;

    @Schema(description = "가맹점명", example = "테스트 가맹점")
    private String merchantName;

    @Schema(description = "주문 ID", example = "ORDER-20240115-001")
    private String orderId;

    @Schema(description = "결제 금액", example = "50000")
    private BigDecimal amount;

    @Schema(description = "통화 코드", example = "KRW")
    private String currency;

    @Schema(description = "결제 상태", example = "APPROVED")
    private PaymentStatus status;

    @Schema(description = "결제 상태 설명", example = "승인됨")
    private String statusDisplayName;

    @Schema(description = "결제 방법", example = "CREDIT_CARD")
    private PaymentMethod paymentMethod;

    @Schema(description = "PG사 거래 ID", example = "PG_TXN_123456")
    private String pgTransactionId;

    @Schema(description = "승인 번호", example = "AP123456")
    private String approvalNumber;

    @Schema(description = "마스킹된 카드 번호", example = "123456******7890")
    private String maskedCardNumber;

    @Schema(description = "고객명", example = "홍길동")
    private String customerName;

    @Schema(description = "고객 이메일", example = "customer@example.com")
    private String customerEmail;

    @Schema(description = "수수료 금액", example = "1650")
    private BigDecimal feeAmount;

    @Schema(description = "부가세 금액", example = "165")
    private BigDecimal vatAmount;

    @Schema(description = "정산 금액", example = "48185")
    private BigDecimal netAmount;

    @Schema(description = "승인 일시")
    private LocalDateTime approvedAt;

    @Schema(description = "취소 일시")
    private LocalDateTime cancelledAt;

    @Schema(description = "생성 일시")
    private LocalDateTime createdAt;

    @Schema(description = "수정 일시")
    private LocalDateTime updatedAt;

    public static PaymentResponse from(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .idempotencyKey(payment.getIdempotencyKey())
                .merchantId(payment.getMerchant().getId())
                .merchantName(payment.getMerchant().getName())
                .orderId(payment.getOrderId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .statusDisplayName(payment.getStatus().getDisplayName())
                .paymentMethod(payment.getPaymentMethod())
                .pgTransactionId(payment.getPgTransactionId())
                .approvalNumber(payment.getApprovalNumber())
                .maskedCardNumber(payment.getMaskedCardNumber())
                .customerName(payment.getCustomerName())
                .customerEmail(payment.getCustomerEmail())
                .feeAmount(payment.getFeeAmount())
                .vatAmount(payment.getVatAmount())
                .netAmount(payment.getNetAmount())
                .approvedAt(payment.getApprovedAt())
                .cancelledAt(payment.getCancelledAt())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
