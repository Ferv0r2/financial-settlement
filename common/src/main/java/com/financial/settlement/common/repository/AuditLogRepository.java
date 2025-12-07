package com.financial.settlement.common.repository;

import com.financial.settlement.common.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * 엔티티별 감사 로그 조회
     */
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, String entityId);

    /**
     * 엔티티 타입별 감사 로그 조회 (페이징)
     */
    Page<AuditLog> findByEntityType(String entityType, Pageable pageable);

    /**
     * 액션별 감사 로그 조회
     */
    Page<AuditLog> findByAction(String action, Pageable pageable);

    /**
     * 기간별 감사 로그 조회
     */
    Page<AuditLog> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /**
     * 액터별 감사 로그 조회
     */
    Page<AuditLog> findByActor(String actor, Pageable pageable);

    /**
     * 특정 엔티티의 상태 변경 이력 조회
     */
    List<AuditLog> findByEntityTypeAndEntityIdAndActionOrderByCreatedAtDesc(
            String entityType, String entityId, String action);
}
