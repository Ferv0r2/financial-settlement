package com.financial.settlement.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financial.settlement.common.config.BeanUtils;
import com.financial.settlement.common.domain.AuditLog;
import com.financial.settlement.common.domain.Payment;
import com.financial.settlement.common.repository.AuditLogRepository;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PreUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * JPA Entity Listener for automatic audit logging
 * JPA EntityListener는 Spring Bean이 아니므로 BeanUtils를 통해 의존성을 가져옴
 */
@Slf4j
public class AuditEventListener {

    // ThreadLocal to store previous state before update
    private static final ThreadLocal<Map<String, Object>> previousStateHolder = new ThreadLocal<>();

    private AuditLogRepository getAuditLogRepository() {
        return BeanUtils.getBean(AuditLogRepository.class);
    }

    private ObjectMapper getObjectMapper() {
        return BeanUtils.getBean(ObjectMapper.class);
    }

    /**
     * 테스트 환경에서는 감사 로그를 비활성화
     */
    private boolean isTestProfile() {
        try {
            Environment env = BeanUtils.getBean(Environment.class);
            if (env == null) return false;
            String[] activeProfiles = env.getActiveProfiles();
            return Arrays.asList(activeProfiles).contains("test");
        } catch (Exception e) {
            return false;
        }
    }

    @PostPersist
    public void onPostPersist(Object entity) {
        if (isTestProfile()) {
            log.debug("Skipping audit log in test profile");
            return;
        }
        try {
            if (entity instanceof Payment payment) {
                saveAuditLog(
                        "Payment",
                        payment.getId().toString(),
                        "CREATE",
                        null,
                        payment.getStatus().name(),
                        null,
                        toJson(createPaymentSnapshot(payment))
                );
            }
        } catch (Exception e) {
            log.warn("Failed to create audit log on persist: {}", e.getMessage());
        }
    }

    @PreUpdate
    public void onPreUpdate(Object entity) {
        if (isTestProfile()) {
            return;
        }
        if (entity instanceof Payment payment) {
            Map<String, Object> previousState = new HashMap<>();
            previousState.put("status", payment.getStatus().name());
            previousState.put("amount", payment.getAmount());
            previousStateHolder.set(previousState);
        }
    }

    @PostUpdate
    public void onPostUpdate(Object entity) {
        if (isTestProfile()) {
            previousStateHolder.remove();
            return;
        }
        if (entity instanceof Payment payment) {
            Map<String, Object> previousState = previousStateHolder.get();
            if (previousState != null) {
                String prevStatus = (String) previousState.get("status");
                String newStatus = payment.getStatus().name();

                if (!prevStatus.equals(newStatus)) {
                    try {
                        saveAuditLog(
                                "Payment",
                                payment.getId().toString(),
                                "STATE_CHANGE",
                                prevStatus,
                                newStatus,
                                toJson(previousState),
                                toJson(createPaymentSnapshot(payment))
                        );
                    } catch (Exception e) {
                        log.warn("Failed to create audit log on update: {}", e.getMessage());
                    }
                }
                previousStateHolder.remove();
            }
        }
    }

    private void saveAuditLog(String entityType, String entityId, String action,
                              String previousState, String newState,
                              String previousData, String newData) {
        AuditLogRepository auditLogRepository = getAuditLogRepository();
        if (auditLogRepository == null) {
            log.warn("AuditLogRepository is not available. Skipping audit log.");
            return;
        }

        try {
            AuditLog auditLog = AuditLog.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .action(action)
                    .previousState(previousState)
                    .newState(newState)
                    .previousData(previousData)
                    .newData(newData)
                    .actor(getCurrentActor())
                    .build();

            auditLogRepository.save(auditLog);
            log.debug("Audit log saved: {} {} {}", entityType, entityId, action);
        } catch (Exception e) {
            log.error("Failed to save audit log: {} {} {}", entityType, entityId, action, e);
        }
    }

    private Map<String, Object> createPaymentSnapshot(Payment payment) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("id", payment.getId().toString());
        snapshot.put("orderId", payment.getOrderId());
        snapshot.put("amount", payment.getAmount());
        snapshot.put("status", payment.getStatus().name());
        snapshot.put("paymentMethod", payment.getPaymentMethod().name());
        if (payment.getPgTransactionId() != null) {
            snapshot.put("pgTransactionId", payment.getPgTransactionId());
        }
        if (payment.getApprovalNumber() != null) {
            snapshot.put("approvalNumber", payment.getApprovalNumber());
        }
        return snapshot;
    }

    private String toJson(Object obj) {
        ObjectMapper objectMapper = getObjectMapper();
        if (objectMapper == null || obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to JSON", e);
            return null;
        }
    }

    private String getCurrentActor() {
        // TODO: SecurityContextHolder에서 현재 사용자 정보 가져오기
        return "SYSTEM";
    }
}
