package com.financial.settlement.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financial.settlement.api.dto.payment.PaymentCreateRequest;
import com.financial.settlement.common.domain.Merchant;
import com.financial.settlement.common.domain.PaymentMethod;
import com.financial.settlement.common.domain.SettlementCycle;
import com.financial.settlement.common.repository.MerchantRepository;
import com.financial.settlement.common.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.financial.settlement.api.config.TestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
@Transactional
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private Merchant testMerchant;

    @BeforeEach
    void setUp() {
        // 테스트용 가맹점 생성
        testMerchant = Merchant.builder()
                .id("TEST_MERCHANT")
                .name("테스트 가맹점")
                .businessNumber("123-45-67890")
                .email("test@example.com")
                .settlementCycle(SettlementCycle.D3)
                .build();
        merchantRepository.save(testMerchant);
    }

    @Test
    @DisplayName("결제 생성 - 성공")
    void createPayment_Success() throws Exception {
        // given
        String idempotencyKey = "test-idem-" + UUID.randomUUID();
        PaymentCreateRequest request = PaymentCreateRequest.builder()
                .merchantId(testMerchant.getId())
                .orderId("ORDER-001")
                .amount(new BigDecimal("50000"))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .cardBin("123456")
                .cardLast4("7890")
                .customerName("홍길동")
                .customerEmail("hong@example.com")
                .build();

        // when & then
        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.merchantId").value(testMerchant.getId()))
                .andExpect(jsonPath("$.data.orderId").value("ORDER-001"))
                .andExpect(jsonPath("$.data.amount").value(50000))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.paymentMethod").value("CREDIT_CARD"))
                .andExpect(jsonPath("$.data.maskedCardNumber").value("123456******7890"));
    }

    @Test
    @DisplayName("결제 생성 - 멱등성 테스트 (동일 키로 재요청)")
    void createPayment_Idempotency() throws Exception {
        // given
        String idempotencyKey = "test-idem-" + UUID.randomUUID();
        PaymentCreateRequest request = PaymentCreateRequest.builder()
                .merchantId(testMerchant.getId())
                .orderId("ORDER-002")
                .amount(new BigDecimal("30000"))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        // 첫 번째 요청
        MvcResult firstResult = mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        // when - 동일한 멱등성 키로 재요청
        MvcResult secondResult = mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        // then - 동일한 결과 반환
        String firstId = objectMapper.readTree(firstResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
        String secondId = objectMapper.readTree(secondResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        assert firstId.equals(secondId) : "멱등성 키로 인해 동일한 결제 ID가 반환되어야 함";
    }

    @Test
    @DisplayName("결제 생성 - Idempotency-Key 헤더 누락")
    void createPayment_MissingIdempotencyKey() throws Exception {
        // given
        PaymentCreateRequest request = PaymentCreateRequest.builder()
                .merchantId(testMerchant.getId())
                .orderId("ORDER-003")
                .amount(new BigDecimal("10000"))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        // when & then
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("I001"));
    }

    @Test
    @DisplayName("결제 생성 - 존재하지 않는 가맹점")
    void createPayment_MerchantNotFound() throws Exception {
        // given
        String idempotencyKey = "test-idem-" + UUID.randomUUID();
        PaymentCreateRequest request = PaymentCreateRequest.builder()
                .merchantId("NON_EXISTENT")
                .orderId("ORDER-004")
                .amount(new BigDecimal("10000"))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        // when & then
        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("M001"));
    }

    @Test
    @DisplayName("결제 생성 - 유효성 검사 실패 (금액 누락)")
    void createPayment_ValidationError() throws Exception {
        // given
        String idempotencyKey = "test-idem-" + UUID.randomUUID();
        PaymentCreateRequest request = PaymentCreateRequest.builder()
                .merchantId(testMerchant.getId())
                .orderId("ORDER-005")
                // amount 누락
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        // when & then
        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C002"));
    }

    @Test
    @DisplayName("결제 조회 - 성공")
    void getPayment_Success() throws Exception {
        // given - 결제 생성
        String idempotencyKey = "test-idem-" + UUID.randomUUID();
        PaymentCreateRequest request = PaymentCreateRequest.builder()
                .merchantId(testMerchant.getId())
                .orderId("ORDER-006")
                .amount(new BigDecimal("25000"))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String paymentId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // when & then
        mockMvc.perform(get("/api/v1/payments/{paymentId}", paymentId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(paymentId))
                .andExpect(jsonPath("$.data.amount").value(25000));
    }

    @Test
    @DisplayName("결제 조회 - 존재하지 않는 결제")
    void getPayment_NotFound() throws Exception {
        // given
        UUID nonExistentId = UUID.randomUUID();

        // when & then
        mockMvc.perform(get("/api/v1/payments/{paymentId}", nonExistentId))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("P001"));
    }

    @Test
    @DisplayName("멱등성 키로 결제 조회 - 성공")
    void getPaymentByIdempotencyKey_Success() throws Exception {
        // given - 결제 생성
        String idempotencyKey = "test-idem-" + UUID.randomUUID();
        PaymentCreateRequest request = PaymentCreateRequest.builder()
                .merchantId(testMerchant.getId())
                .orderId("ORDER-007")
                .amount(new BigDecimal("15000"))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // when & then
        mockMvc.perform(get("/api/v1/payments/by-idempotency-key/{idempotencyKey}", idempotencyKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.idempotencyKey").value(idempotencyKey))
                .andExpect(jsonPath("$.data.amount").value(15000));
    }

    @Test
    @DisplayName("가맹점별 결제 목록 조회 - 성공")
    void getPaymentsByMerchant_Success() throws Exception {
        // given - 결제 2건 생성
        for (int i = 1; i <= 2; i++) {
            String idempotencyKey = "test-idem-list-" + i + "-" + UUID.randomUUID();
            PaymentCreateRequest request = PaymentCreateRequest.builder()
                    .merchantId(testMerchant.getId())
                    .orderId("ORDER-LIST-" + i)
                    .amount(new BigDecimal("10000").multiply(new BigDecimal(i)))
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .build();

            mockMvc.perform(post("/api/v1/payments")
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        // when & then
        mockMvc.perform(get("/api/v1/payments/merchant/{merchantId}", testMerchant.getId())
                        .param("page", "0")
                        .param("size", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @DisplayName("결제 히스토리 조회 - 성공")
    void getPaymentHistory_Success() throws Exception {
        // given - 결제 생성
        String idempotencyKey = "test-idem-" + UUID.randomUUID();
        PaymentCreateRequest request = PaymentCreateRequest.builder()
                .merchantId(testMerchant.getId())
                .orderId("ORDER-HISTORY")
                .amount(new BigDecimal("50000"))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String paymentId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // when & then
        mockMvc.perform(get("/api/v1/payments/{paymentId}/history", paymentId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentId").value(paymentId));
    }
}
