package com.financial.settlement.api.controller;

import com.financial.settlement.api.dto.payment.PaymentCreateRequest;
import com.financial.settlement.api.dto.payment.PaymentHistoryResponse;
import com.financial.settlement.api.dto.payment.PaymentResponse;
import com.financial.settlement.api.service.PaymentService;
import com.financial.settlement.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "결제 API")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "결제 생성", description = "새로운 결제를 생성합니다. Idempotency-Key 헤더가 필수입니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "결제 생성 성공",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "중복 요청 (멱등성 키 충돌)"
            )
    })
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @Parameter(description = "멱등성 키 (중복 결제 방지용)", required = true, example = "idem-20240115-001")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentCreateRequest request
    ) {
        log.info("POST /api/v1/payments - idempotencyKey: {}", idempotencyKey);

        PaymentResponse response = paymentService.createPayment(idempotencyKey, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "결제 조회", description = "결제 ID로 결제 정보를 조회합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "결제를 찾을 수 없음"
            )
    })
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @Parameter(description = "결제 ID", required = true)
            @PathVariable UUID paymentId
    ) {
        log.info("GET /api/v1/payments/{}", paymentId);

        PaymentResponse response = paymentService.getPayment(paymentId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/by-idempotency-key/{idempotencyKey}")
    @Operation(summary = "멱등성 키로 결제 조회", description = "멱등성 키로 결제 정보를 조회합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "결제를 찾을 수 없음"
            )
    })
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByIdempotencyKey(
            @Parameter(description = "멱등성 키", required = true)
            @PathVariable String idempotencyKey
    ) {
        log.info("GET /api/v1/payments/by-idempotency-key/{}", idempotencyKey);

        PaymentResponse response = paymentService.getPaymentByIdempotencyKey(idempotencyKey);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/merchant/{merchantId}")
    @Operation(summary = "가맹점별 결제 목록 조회", description = "가맹점의 결제 목록을 페이징하여 조회합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "가맹점을 찾을 수 없음"
            )
    })
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> getPaymentsByMerchant(
            @Parameter(description = "가맹점 ID", required = true)
            @PathVariable String merchantId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        log.info("GET /api/v1/payments/merchant/{} - page: {}, size: {}",
                merchantId, pageable.getPageNumber(), pageable.getPageSize());

        Page<PaymentResponse> response = paymentService.getPaymentsByMerchant(merchantId, pageable);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{paymentId}/history")
    @Operation(summary = "결제 히스토리 조회", description = "결제의 상태 변경 이력을 조회합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = PaymentHistoryResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "결제를 찾을 수 없음"
            )
    })
    public ResponseEntity<ApiResponse<PaymentHistoryResponse>> getPaymentHistory(
            @Parameter(description = "결제 ID", required = true)
            @PathVariable UUID paymentId
    ) {
        log.info("GET /api/v1/payments/{}/history", paymentId);

        PaymentHistoryResponse response = paymentService.getPaymentHistory(paymentId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
