package com.financial.settlement.common.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 감사 로그 엔티티
 */
@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 32)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 64)
    private String entityId;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "previous_state", length = 20)
    private String previousState;

    @Column(name = "new_state", length = 20)
    private String newState;

    @Column(name = "previous_data", columnDefinition = "jsonb")
    private String previousData;

    @Column(name = "new_data", columnDefinition = "jsonb")
    private String newData;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "actor", length = 64)
    private String actor;

    @Column(name = "actor_ip", length = 45)
    private String actorIp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public AuditLog(String entityType, String entityId, String action,
                    String previousState, String newState,
                    String previousData, String newData,
                    String reason, String actor, String actorIp) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.previousState = previousState;
        this.newState = newState;
        this.previousData = previousData;
        this.newData = newData;
        this.reason = reason;
        this.actor = actor;
        this.actorIp = actorIp;
    }

    /**
     * 생성 이벤트용 팩토리 메서드
     */
    public static AuditLog createEvent(String entityType, String entityId, String newData, String actor) {
        return AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action("CREATE")
                .newData(newData)
                .actor(actor)
                .build();
    }

    /**
     * 상태 변경 이벤트용 팩토리 메서드
     */
    public static AuditLog stateChangeEvent(String entityType, String entityId,
                                            String previousState, String newState,
                                            String actor) {
        return AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action("STATE_CHANGE")
                .previousState(previousState)
                .newState(newState)
                .actor(actor)
                .build();
    }

    /**
     * 업데이트 이벤트용 팩토리 메서드
     */
    public static AuditLog updateEvent(String entityType, String entityId,
                                       String previousData, String newData,
                                       String actor) {
        return AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action("UPDATE")
                .previousData(previousData)
                .newData(newData)
                .actor(actor)
                .build();
    }
}
