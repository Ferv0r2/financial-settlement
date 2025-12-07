package com.financial.settlement.common.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PaymentStatus 상태 머신 테스트")
class PaymentStatusTest {

    @Nested
    @DisplayName("상태 전이 규칙 테스트")
    class StateTransitionTest {

        @Test
        @DisplayName("REQUESTED 상태에서 PROCESSING, FAILED로 전이 가능")
        void requestedCanTransitionToProcessingOrFailed() {
            Set<PaymentStatus> allowedTransitions = PaymentStatus.REQUESTED.getAllowedTransitions();

            assertThat(allowedTransitions)
                    .containsExactlyInAnyOrder(PaymentStatus.PROCESSING, PaymentStatus.FAILED);
        }

        @Test
        @DisplayName("PROCESSING 상태에서 APPROVED, FAILED로 전이 가능")
        void processingCanTransitionToApprovedOrFailed() {
            Set<PaymentStatus> allowedTransitions = PaymentStatus.PROCESSING.getAllowedTransitions();

            assertThat(allowedTransitions)
                    .containsExactlyInAnyOrder(PaymentStatus.APPROVED, PaymentStatus.FAILED);
        }

        @Test
        @DisplayName("APPROVED 상태에서 CLEARED, CANCELLED로 전이 가능")
        void approvedCanTransitionToClearedOrCancelled() {
            Set<PaymentStatus> allowedTransitions = PaymentStatus.APPROVED.getAllowedTransitions();

            assertThat(allowedTransitions)
                    .containsExactlyInAnyOrder(PaymentStatus.CLEARED, PaymentStatus.CANCELLED);
        }

        @Test
        @DisplayName("CLEARED 상태에서 SETTLED로만 전이 가능")
        void clearedCanTransitionToSettledOnly() {
            Set<PaymentStatus> allowedTransitions = PaymentStatus.CLEARED.getAllowedTransitions();

            assertThat(allowedTransitions)
                    .containsExactly(PaymentStatus.SETTLED);
        }

        @Test
        @DisplayName("SETTLED 상태에서 REFUNDED로만 전이 가능")
        void settledCanTransitionToRefundedOnly() {
            Set<PaymentStatus> allowedTransitions = PaymentStatus.SETTLED.getAllowedTransitions();

            assertThat(allowedTransitions)
                    .containsExactly(PaymentStatus.REFUNDED);
        }

        @ParameterizedTest
        @EnumSource(value = PaymentStatus.class, names = {"FAILED", "CANCELLED", "REFUNDED"})
        @DisplayName("최종 상태(FAILED, CANCELLED, REFUNDED)에서는 전이 불가")
        void finalStatesCannotTransition(PaymentStatus finalStatus) {
            Set<PaymentStatus> allowedTransitions = finalStatus.getAllowedTransitions();

            assertThat(allowedTransitions).isEmpty();
            assertThat(finalStatus.isFinalState()).isTrue();
        }
    }

    @Nested
    @DisplayName("canTransitionTo 메서드 테스트")
    class CanTransitionToTest {

        @ParameterizedTest
        @CsvSource({
                "REQUESTED, PROCESSING, true",
                "REQUESTED, FAILED, true",
                "REQUESTED, APPROVED, false",
                "PROCESSING, APPROVED, true",
                "PROCESSING, FAILED, true",
                "PROCESSING, CANCELLED, false",
                "APPROVED, CLEARED, true",
                "APPROVED, CANCELLED, true",
                "APPROVED, REFUNDED, false",
                "CLEARED, SETTLED, true",
                "CLEARED, REFUNDED, false",
                "SETTLED, REFUNDED, true",
                "SETTLED, CANCELLED, false"
        })
        @DisplayName("상태 전이 가능 여부 확인")
        void canTransitionToShouldReturnCorrectResult(PaymentStatus from, PaymentStatus to, boolean expected) {
            assertThat(from.canTransitionTo(to)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("validateTransition 메서드 테스트")
    class ValidateTransitionTest {

        @Test
        @DisplayName("유효한 전이는 예외를 발생시키지 않음")
        void validTransitionShouldNotThrowException() {
            assertThatCode(() -> PaymentStatus.REQUESTED.validateTransition(PaymentStatus.PROCESSING))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("유효하지 않은 전이는 InvalidStateTransitionException 발생")
        void invalidTransitionShouldThrowException() {
            assertThatThrownBy(() -> PaymentStatus.REQUESTED.validateTransition(PaymentStatus.SETTLED))
                    .isInstanceOf(PaymentStatus.InvalidStateTransitionException.class)
                    .hasMessageContaining("Cannot transition from REQUESTED to SETTLED");
        }

        @Test
        @DisplayName("예외에서 from, to 상태 정보 확인 가능")
        void exceptionShouldContainStatusInfo() {
            try {
                PaymentStatus.FAILED.validateTransition(PaymentStatus.APPROVED);
                fail("Expected InvalidStateTransitionException");
            } catch (PaymentStatus.InvalidStateTransitionException e) {
                assertThat(e.getFromStatus()).isEqualTo(PaymentStatus.FAILED);
                assertThat(e.getToStatus()).isEqualTo(PaymentStatus.APPROVED);
            }
        }
    }

    @Nested
    @DisplayName("상태 속성 테스트")
    class StatusPropertiesTest {

        @Test
        @DisplayName("APPROVED 상태만 취소 가능")
        void onlyApprovedIsCancellable() {
            for (PaymentStatus status : PaymentStatus.values()) {
                if (status == PaymentStatus.APPROVED) {
                    assertThat(status.isCancellable()).isTrue();
                } else {
                    assertThat(status.isCancellable())
                            .as("Status %s should not be cancellable", status)
                            .isFalse();
                }
            }
        }

        @Test
        @DisplayName("SETTLED 상태만 환불 가능")
        void onlySettledIsRefundable() {
            for (PaymentStatus status : PaymentStatus.values()) {
                if (status == PaymentStatus.SETTLED) {
                    assertThat(status.isRefundable()).isTrue();
                } else {
                    assertThat(status.isRefundable())
                            .as("Status %s should not be refundable", status)
                            .isFalse();
                }
            }
        }

        @Test
        @DisplayName("성공 상태 확인 (APPROVED, CLEARED, SETTLED)")
        void successfulStatusesCheck() {
            assertThat(PaymentStatus.APPROVED.isSuccessful()).isTrue();
            assertThat(PaymentStatus.CLEARED.isSuccessful()).isTrue();
            assertThat(PaymentStatus.SETTLED.isSuccessful()).isTrue();

            assertThat(PaymentStatus.REQUESTED.isSuccessful()).isFalse();
            assertThat(PaymentStatus.PROCESSING.isSuccessful()).isFalse();
            assertThat(PaymentStatus.FAILED.isSuccessful()).isFalse();
            assertThat(PaymentStatus.CANCELLED.isSuccessful()).isFalse();
            assertThat(PaymentStatus.REFUNDED.isSuccessful()).isFalse();
        }
    }

    @Nested
    @DisplayName("전체 결제 흐름 시나리오 테스트")
    class PaymentFlowScenarioTest {

        @Test
        @DisplayName("정상 결제 흐름: REQUESTED → PROCESSING → APPROVED → CLEARED → SETTLED")
        void normalPaymentFlow() {
            PaymentStatus status = PaymentStatus.REQUESTED;

            assertThat(status.canTransitionTo(PaymentStatus.PROCESSING)).isTrue();
            status = PaymentStatus.PROCESSING;

            assertThat(status.canTransitionTo(PaymentStatus.APPROVED)).isTrue();
            status = PaymentStatus.APPROVED;

            assertThat(status.canTransitionTo(PaymentStatus.CLEARED)).isTrue();
            status = PaymentStatus.CLEARED;

            assertThat(status.canTransitionTo(PaymentStatus.SETTLED)).isTrue();
            status = PaymentStatus.SETTLED;

            assertThat(status.isFinalState()).isFalse(); // 환불 가능하므로
            assertThat(status.isRefundable()).isTrue();
        }

        @Test
        @DisplayName("결제 취소 흐름: REQUESTED → PROCESSING → APPROVED → CANCELLED")
        void cancellationFlow() {
            PaymentStatus status = PaymentStatus.REQUESTED;
            status = PaymentStatus.PROCESSING;
            status = PaymentStatus.APPROVED;

            assertThat(status.isCancellable()).isTrue();
            assertThat(status.canTransitionTo(PaymentStatus.CANCELLED)).isTrue();

            status = PaymentStatus.CANCELLED;
            assertThat(status.isFinalState()).isTrue();
        }

        @Test
        @DisplayName("역정산(환불) 흐름: SETTLED → REFUNDED")
        void refundFlow() {
            PaymentStatus status = PaymentStatus.SETTLED;

            assertThat(status.isRefundable()).isTrue();
            assertThat(status.canTransitionTo(PaymentStatus.REFUNDED)).isTrue();

            status = PaymentStatus.REFUNDED;
            assertThat(status.isFinalState()).isTrue();
        }

        @Test
        @DisplayName("결제 실패 흐름: REQUESTED → FAILED")
        void failureFromRequestedFlow() {
            PaymentStatus status = PaymentStatus.REQUESTED;

            assertThat(status.canTransitionTo(PaymentStatus.FAILED)).isTrue();
            status = PaymentStatus.FAILED;

            assertThat(status.isFinalState()).isTrue();
        }

        @Test
        @DisplayName("PG 처리 중 실패 흐름: PROCESSING → FAILED")
        void failureFromProcessingFlow() {
            PaymentStatus status = PaymentStatus.PROCESSING;

            assertThat(status.canTransitionTo(PaymentStatus.FAILED)).isTrue();
            status = PaymentStatus.FAILED;

            assertThat(status.isFinalState()).isTrue();
        }
    }

    @Nested
    @DisplayName("불가능한 상태 전이 시나리오 테스트")
    class InvalidTransitionScenarioTest {

        @Test
        @DisplayName("REQUESTED에서 바로 APPROVED로 전이 불가")
        void cannotSkipProcessing() {
            assertThat(PaymentStatus.REQUESTED.canTransitionTo(PaymentStatus.APPROVED)).isFalse();
        }

        @Test
        @DisplayName("APPROVED에서 바로 SETTLED로 전이 불가 (CLEARED 필요)")
        void cannotSkipClearing() {
            assertThat(PaymentStatus.APPROVED.canTransitionTo(PaymentStatus.SETTLED)).isFalse();
        }

        @Test
        @DisplayName("CLEARED에서 취소 불가")
        void cannotCancelAfterClearing() {
            assertThat(PaymentStatus.CLEARED.canTransitionTo(PaymentStatus.CANCELLED)).isFalse();
            assertThat(PaymentStatus.CLEARED.isCancellable()).isFalse();
        }

        @Test
        @DisplayName("APPROVED에서 환불 불가 (정산 후에만 가능)")
        void cannotRefundBeforeSettlement() {
            assertThat(PaymentStatus.APPROVED.canTransitionTo(PaymentStatus.REFUNDED)).isFalse();
            assertThat(PaymentStatus.APPROVED.isRefundable()).isFalse();
        }

        @Test
        @DisplayName("FAILED에서 어떤 상태로도 전이 불가")
        void cannotTransitionFromFailed() {
            for (PaymentStatus status : PaymentStatus.values()) {
                assertThat(PaymentStatus.FAILED.canTransitionTo(status))
                        .as("FAILED should not transition to %s", status)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("CANCELLED에서 어떤 상태로도 전이 불가")
        void cannotTransitionFromCancelled() {
            for (PaymentStatus status : PaymentStatus.values()) {
                assertThat(PaymentStatus.CANCELLED.canTransitionTo(status))
                        .as("CANCELLED should not transition to %s", status)
                        .isFalse();
            }
        }
    }

    @Nested
    @DisplayName("fromString 메서드 테스트")
    class FromStringTest {

        @Test
        @DisplayName("대문자 문자열로부터 상태 변환")
        void fromUppercaseString() {
            assertThat(PaymentStatus.fromString("REQUESTED")).isEqualTo(PaymentStatus.REQUESTED);
            assertThat(PaymentStatus.fromString("APPROVED")).isEqualTo(PaymentStatus.APPROVED);
        }

        @Test
        @DisplayName("소문자 문자열로부터 상태 변환")
        void fromLowercaseString() {
            assertThat(PaymentStatus.fromString("requested")).isEqualTo(PaymentStatus.REQUESTED);
            assertThat(PaymentStatus.fromString("approved")).isEqualTo(PaymentStatus.APPROVED);
        }

        @Test
        @DisplayName("알 수 없는 문자열은 IllegalArgumentException 발생")
        void unknownStringShouldThrowException() {
            assertThatThrownBy(() -> PaymentStatus.fromString("UNKNOWN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown payment status");
        }
    }

    @Nested
    @DisplayName("displayName 및 description 테스트")
    class DisplayNameTest {

        @Test
        @DisplayName("모든 상태에 displayName이 존재")
        void allStatusesHaveDisplayName() {
            for (PaymentStatus status : PaymentStatus.values()) {
                assertThat(status.getDisplayName()).isNotBlank();
            }
        }

        @Test
        @DisplayName("모든 상태에 description이 존재")
        void allStatusesHaveDescription() {
            for (PaymentStatus status : PaymentStatus.values()) {
                assertThat(status.getDescription()).isNotBlank();
            }
        }
    }
}
