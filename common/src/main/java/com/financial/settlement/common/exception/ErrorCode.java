package com.financial.settlement.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common Errors (C)
    INTERNAL_SERVER_ERROR("C001", "내부 서버 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_INPUT_VALUE("C002", "잘못된 입력값입니다.", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND("C003", "리소스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("C004", "허용되지 않은 메서드입니다.", HttpStatus.METHOD_NOT_ALLOWED),
    ACCESS_DENIED("C005", "접근이 거부되었습니다.", HttpStatus.FORBIDDEN),
    UNAUTHORIZED("C006", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED),

    // Payment Errors (P)
    PAYMENT_NOT_FOUND("P001", "결제 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    PAYMENT_ALREADY_EXISTS("P002", "이미 존재하는 결제입니다.", HttpStatus.CONFLICT),
    PAYMENT_INVALID_STATE_TRANSITION("P003", "잘못된 상태 전이입니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_ALREADY_CANCELLED("P004", "이미 취소된 결제입니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_CANNOT_CANCEL("P005", "취소할 수 없는 상태입니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_AMOUNT_MISMATCH("P006", "결제 금액이 일치하지 않습니다.", HttpStatus.BAD_REQUEST),

    // Idempotency Errors (I)
    IDEMPOTENCY_KEY_REQUIRED("I001", "Idempotency-Key 헤더가 필요합니다.", HttpStatus.BAD_REQUEST),
    IDEMPOTENCY_KEY_CONFLICT("I002", "동일한 키로 다른 요청이 처리 중입니다.", HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_MISMATCH("I003", "동일한 키로 다른 내용의 요청이 있었습니다.", HttpStatus.CONFLICT),

    // PG Errors (G)
    PG_CONNECTION_ERROR("G001", "PG사 연결에 실패했습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    PG_TIMEOUT("G002", "PG사 응답 시간이 초과되었습니다.", HttpStatus.GATEWAY_TIMEOUT),
    PG_INSUFFICIENT_BALANCE("G003", "잔액이 부족합니다.", HttpStatus.BAD_REQUEST),
    PG_EXPIRED_CARD("G004", "카드가 만료되었습니다.", HttpStatus.BAD_REQUEST),
    PG_INVALID_CARD("G005", "유효하지 않은 카드입니다.", HttpStatus.BAD_REQUEST),
    PG_AUTHENTICATION_REQUIRED("G006", "추가 인증이 필요합니다.", HttpStatus.PAYMENT_REQUIRED),

    // Settlement Errors (S)
    SETTLEMENT_NOT_FOUND("S001", "정산 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    SETTLEMENT_ALREADY_PROCESSED("S002", "이미 처리된 정산입니다.", HttpStatus.BAD_REQUEST),
    SETTLEMENT_FAILED("S003", "정산 처리에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),

    // Bank Errors (B)
    BANK_CONNECTION_ERROR("B001", "은행 연결에 실패했습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    BANK_MAINTENANCE("B002", "은행 점검 중입니다.", HttpStatus.SERVICE_UNAVAILABLE),
    BANK_DAILY_LIMIT_EXCEEDED("B003", "일일 이체 한도를 초과했습니다.", HttpStatus.BAD_REQUEST),
    BANK_INVALID_ACCOUNT("B004", "유효하지 않은 계좌입니다.", HttpStatus.BAD_REQUEST),

    // Merchant Errors (M)
    MERCHANT_NOT_FOUND("M001", "가맹점 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    MERCHANT_INACTIVE("M002", "비활성화된 가맹점입니다.", HttpStatus.BAD_REQUEST),

    // Concurrency Errors (X)
    LOCK_ACQUISITION_FAILED("X001", "잠금 획득에 실패했습니다.", HttpStatus.CONFLICT),
    OPTIMISTIC_LOCK_FAILED("X002", "동시 수정이 감지되었습니다.", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
