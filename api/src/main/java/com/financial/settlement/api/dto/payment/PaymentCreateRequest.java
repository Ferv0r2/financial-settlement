package com.financial.settlement.api.dto.payment;

import com.financial.settlement.common.domain.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@Schema(description = "결제 생성 요청")
public class PaymentCreateRequest {

    @NotBlank(message = "가맹점 ID는 필수입니다")
    @Size(max = 32, message = "가맹점 ID는 32자 이하여야 합니다")
    @Schema(description = "가맹점 ID", example = "MERCHANT_001")
    private String merchantId;

    @NotBlank(message = "주문 ID는 필수입니다")
    @Size(max = 64, message = "주문 ID는 64자 이하여야 합니다")
    @Schema(description = "주문 ID (가맹점 측 고유 주문번호)", example = "ORDER-20240115-001")
    private String orderId;

    @NotNull(message = "결제 금액은 필수입니다")
    @DecimalMin(value = "100", message = "최소 결제 금액은 100원입니다")
    @DecimalMax(value = "100000000", message = "최대 결제 금액은 1억원입니다")
    @Digits(integer = 15, fraction = 2, message = "금액 형식이 올바르지 않습니다")
    @Schema(description = "결제 금액", example = "50000")
    private BigDecimal amount;

    @Schema(description = "통화 코드 (기본값: KRW)", example = "KRW")
    private String currency;

    @NotNull(message = "결제 방법은 필수입니다")
    @Schema(description = "결제 방법", example = "CREDIT_CARD")
    private PaymentMethod paymentMethod;

    @Size(max = 6, message = "카드 BIN은 6자리입니다")
    @Pattern(regexp = "^[0-9]*$", message = "카드 BIN은 숫자만 가능합니다")
    @Schema(description = "카드 앞 6자리 (BIN)", example = "123456")
    private String cardBin;

    @Size(max = 4, message = "카드 뒷자리는 4자리입니다")
    @Pattern(regexp = "^[0-9]*$", message = "카드 뒷자리는 숫자만 가능합니다")
    @Schema(description = "카드 뒤 4자리", example = "7890")
    private String cardLast4;

    @Size(max = 50, message = "고객명은 50자 이하여야 합니다")
    @Schema(description = "고객명", example = "홍길동")
    private String customerName;

    @Email(message = "올바른 이메일 형식이 아닙니다")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다")
    @Schema(description = "고객 이메일", example = "customer@example.com")
    private String customerEmail;
}
