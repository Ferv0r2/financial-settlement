package com.financial.settlement.api.dto.payment;

import com.financial.settlement.common.domain.AuditLog;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "결제 히스토리 응답")
public class PaymentHistoryResponse {

    @Schema(description = "결제 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String paymentId;

    @Schema(description = "히스토리 목록")
    private List<HistoryItem> history;

    @Getter
    @Builder
    @Schema(description = "히스토리 항목")
    public static class HistoryItem {

        @Schema(description = "로그 ID", example = "1")
        private Long id;

        @Schema(description = "액션", example = "STATUS_CHANGE")
        private String action;

        @Schema(description = "이전 상태", example = "REQUESTED")
        private String previousState;

        @Schema(description = "새로운 상태", example = "PROCESSING")
        private String newState;

        @Schema(description = "사유")
        private String reason;

        @Schema(description = "수행자", example = "SYSTEM")
        private String actor;

        @Schema(description = "수행자 IP", example = "127.0.0.1")
        private String actorIp;

        @Schema(description = "발생 일시")
        private LocalDateTime createdAt;

        public static HistoryItem from(AuditLog log) {
            return HistoryItem.builder()
                    .id(log.getId())
                    .action(log.getAction())
                    .previousState(log.getPreviousState())
                    .newState(log.getNewState())
                    .reason(log.getReason())
                    .actor(log.getActor())
                    .actorIp(log.getActorIp())
                    .createdAt(log.getCreatedAt())
                    .build();
        }
    }
}
