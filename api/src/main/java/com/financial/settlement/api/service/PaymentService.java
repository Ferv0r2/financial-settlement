package com.financial.settlement.api.service;

import com.financial.settlement.api.dto.payment.PaymentCreateRequest;
import com.financial.settlement.api.dto.payment.PaymentHistoryResponse;
import com.financial.settlement.api.dto.payment.PaymentResponse;
import com.financial.settlement.common.domain.AuditLog;
import com.financial.settlement.common.domain.Merchant;
import com.financial.settlement.common.domain.Payment;
import com.financial.settlement.common.exception.BusinessException;
import com.financial.settlement.common.exception.ErrorCode;
import com.financial.settlement.common.repository.AuditLogRepository;
import com.financial.settlement.common.repository.MerchantRepository;
import com.financial.settlement.common.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final MerchantRepository merchantRepository;
    private final AuditLogRepository auditLogRepository;

    /**
     * 결제 생성
     */
    @Transactional
    public PaymentResponse createPayment(String idempotencyKey, PaymentCreateRequest request) {
        log.info("Creating payment with idempotencyKey: {}, merchantId: {}, orderId: {}",
                idempotencyKey, request.getMerchantId(), request.getOrderId());

        // 멱등성 체크: 이미 같은 키로 결제가 존재하면 기존 결과 반환
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(existingPayment -> {
                    log.info("Found existing payment for idempotencyKey: {}", idempotencyKey);
                    return PaymentResponse.from(existingPayment);
                })
                .orElseGet(() -> processNewPayment(idempotencyKey, request));
    }

    private PaymentResponse processNewPayment(String idempotencyKey, PaymentCreateRequest request) {
        // 가맹점 조회 및 검증
        Merchant merchant = merchantRepository.findById(request.getMerchantId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MERCHANT_NOT_FOUND,
                        "merchantId: " + request.getMerchantId()
                ));

        // 가맹점 상태 확인
        if (!merchant.canProcessPayments()) {
            throw new BusinessException(
                    ErrorCode.MERCHANT_INACTIVE,
                    "merchantId: " + request.getMerchantId() + ", status: " + merchant.getStatus()
            );
        }

        // Payment 엔티티 생성
        Payment payment = Payment.builder()
                .idempotencyKey(idempotencyKey)
                .merchant(merchant)
                .orderId(request.getOrderId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .paymentMethod(request.getPaymentMethod())
                .cardBin(request.getCardBin())
                .cardLast4(request.getCardLast4())
                .customerName(request.getCustomerName())
                .customerEmail(request.getCustomerEmail())
                .build();

        // 결제 처리 시작 (REQUESTED → PROCESSING)
        payment.startProcessing();

        // Mock PG 연동 (Sprint 2.1에서 구현 예정)
        // 현재는 바로 승인 처리
        String pgTransactionId = "PG_TXN_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String approvalNumber = "AP" + System.currentTimeMillis() % 1000000;
        payment.approve(pgTransactionId, approvalNumber);

        // 저장
        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment created successfully: id={}, status={}", savedPayment.getId(), savedPayment.getStatus());

        return PaymentResponse.from(savedPayment);
    }

    /**
     * 결제 단건 조회
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PAYMENT_NOT_FOUND,
                        "paymentId: " + paymentId
                ));

        return PaymentResponse.from(payment);
    }

    /**
     * 멱등성 키로 결제 조회
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByIdempotencyKey(String idempotencyKey) {
        Payment payment = paymentRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PAYMENT_NOT_FOUND,
                        "idempotencyKey: " + idempotencyKey
                ));

        return PaymentResponse.from(payment);
    }

    /**
     * 가맹점별 결제 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPaymentsByMerchant(String merchantId, Pageable pageable) {
        // 가맹점 존재 확인
        if (!merchantRepository.existsById(merchantId)) {
            throw new BusinessException(
                    ErrorCode.MERCHANT_NOT_FOUND,
                    "merchantId: " + merchantId
            );
        }

        return paymentRepository.findByMerchantId(merchantId, pageable)
                .map(PaymentResponse::from);
    }

    /**
     * 결제 히스토리 조회
     */
    @Transactional(readOnly = true)
    public PaymentHistoryResponse getPaymentHistory(UUID paymentId) {
        // 결제 존재 확인
        if (!paymentRepository.existsById(paymentId)) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_NOT_FOUND,
                    "paymentId: " + paymentId
            );
        }

        List<AuditLog> logs = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "Payment",
                paymentId.toString()
        );

        List<PaymentHistoryResponse.HistoryItem> historyItems = logs.stream()
                .map(PaymentHistoryResponse.HistoryItem::from)
                .toList();

        return PaymentHistoryResponse.builder()
                .paymentId(paymentId.toString())
                .history(historyItems)
                .build();
    }
}
