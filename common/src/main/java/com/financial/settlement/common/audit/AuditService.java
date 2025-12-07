package com.financial.settlement.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financial.settlement.common.domain.AuditLog;
import com.financial.settlement.common.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 감사 로그 서비스
 * EntityListener가 처리하지 못하는 경우 수동으로 감사 로그 기록
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * 상태 변경 감사 로그 기록
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logStateChange(String entityType, String entityId,
                               String previousState, String newState,
                               String actor, String reason) {
        AuditLog auditLog = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action("STATE_CHANGE")
                .previousState(previousState)
                .newState(newState)
                .reason(reason)
                .actor(actor)
                .build();

        auditLogRepository.save(auditLog);
        log.info("State change logged: {} {} -> {} ({})", entityType, previousState, newState, entityId);
    }

    /**
     * 생성 감사 로그 기록
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logCreate(String entityType, String entityId, Object data, String actor) {
        AuditLog auditLog = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action("CREATE")
                .newData(toJson(data))
                .actor(actor)
                .build();

        auditLogRepository.save(auditLog);
        log.info("Create logged: {} {}", entityType, entityId);
    }

    /**
     * 업데이트 감사 로그 기록
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logUpdate(String entityType, String entityId,
                          Object previousData, Object newData, String actor) {
        AuditLog auditLog = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action("UPDATE")
                .previousData(toJson(previousData))
                .newData(toJson(newData))
                .actor(actor)
                .build();

        auditLogRepository.save(auditLog);
        log.info("Update logged: {} {}", entityType, entityId);
    }

    /**
     * 커스텀 액션 감사 로그 기록
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(String entityType, String entityId, String action,
                          String reason, String actor, String actorIp) {
        AuditLog auditLog = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .reason(reason)
                .actor(actor)
                .actorIp(actorIp)
                .build();

        auditLogRepository.save(auditLog);
        log.info("Action logged: {} {} {}", action, entityType, entityId);
    }

    /**
     * 엔티티 히스토리 조회
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getEntityHistory(String entityType, String entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
    }

    /**
     * 엔티티 상태 변경 이력 조회
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getStateChangeHistory(String entityType, String entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdAndActionOrderByCreatedAtDesc(
                entityType, entityId, "STATE_CHANGE");
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to JSON", e);
            return null;
        }
    }
}
