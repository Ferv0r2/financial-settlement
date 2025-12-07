package com.financial.settlement.common.repository;

import com.financial.settlement.common.domain.Payment;
import com.financial.settlement.common.domain.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * 멱등성 키로 결제 조회
     */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * 주문 ID로 결제 조회
     */
    Optional<Payment> findByOrderId(String orderId);

    /**
     * 가맹점별 결제 목록 조회
     */
    Page<Payment> findByMerchantId(String merchantId, Pageable pageable);

    /**
     * 가맹점별 특정 상태의 결제 목록 조회
     */
    Page<Payment> findByMerchantIdAndStatus(String merchantId, PaymentStatus status, Pageable pageable);

    /**
     * 특정 상태의 결제 목록 조회
     */
    List<Payment> findByStatus(PaymentStatus status);

    /**
     * 청산 대상 결제 조회 (APPROVED 상태, 특정 시간 이전)
     */
    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.approvedAt < :cutoffTime")
    List<Payment> findPaymentsForClearing(
            @Param("status") PaymentStatus status,
            @Param("cutoffTime") LocalDateTime cutoffTime
    );

    /**
     * 가맹점별 기간 내 결제 합계
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "WHERE p.merchant.id = :merchantId " +
            "AND p.status IN :statuses " +
            "AND p.createdAt BETWEEN :startDate AND :endDate")
    java.math.BigDecimal sumAmountByMerchantAndStatusAndPeriod(
            @Param("merchantId") String merchantId,
            @Param("statuses") List<PaymentStatus> statuses,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * 가맹점별 기간 내 결제 건수
     */
    @Query("SELECT COUNT(p) FROM Payment p " +
            "WHERE p.merchant.id = :merchantId " +
            "AND p.status IN :statuses " +
            "AND p.createdAt BETWEEN :startDate AND :endDate")
    long countByMerchantAndStatusAndPeriod(
            @Param("merchantId") String merchantId,
            @Param("statuses") List<PaymentStatus> statuses,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * 멱등성 키 존재 여부 확인
     */
    boolean existsByIdempotencyKey(String idempotencyKey);
}
