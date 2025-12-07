package com.financial.settlement.common.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가맹점 엔티티
 */
@Entity
@Table(name = "merchants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Merchant extends BaseEntity {

    @Id
    @Column(name = "id", length = 32)
    private String id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "business_number", nullable = false, unique = true, length = 20)
    private String businessNumber;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "bank_code", length = 10)
    private String bankCode;

    @Column(name = "account_number", length = 30)
    private String accountNumber;

    @Column(name = "account_holder", length = 50)
    private String accountHolder;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_cycle", nullable = false, length = 20)
    private SettlementCycle settlementCycle = SettlementCycle.D3;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MerchantStatus status = MerchantStatus.ACTIVE;

    @Version
    @Column(name = "version")
    private Long version = 0L;

    @Builder
    public Merchant(String id, String name, String businessNumber, String email,
                    String phone, String bankCode, String accountNumber,
                    String accountHolder, SettlementCycle settlementCycle) {
        this.id = id;
        this.name = name;
        this.businessNumber = businessNumber;
        this.email = email;
        this.phone = phone;
        this.bankCode = bankCode;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.settlementCycle = settlementCycle != null ? settlementCycle : SettlementCycle.D3;
        this.status = MerchantStatus.ACTIVE;
    }

    /**
     * 가맹점 상태 변경
     */
    public void changeStatus(MerchantStatus newStatus) {
        this.status = newStatus;
    }

    /**
     * 결제 처리 가능 여부
     */
    public boolean canProcessPayments() {
        return status.canProcessPayments();
    }

    /**
     * 정산 정보 업데이트
     */
    public void updateSettlementInfo(String bankCode, String accountNumber, String accountHolder) {
        this.bankCode = bankCode;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
    }

    /**
     * 기본 정보 업데이트
     */
    public void updateInfo(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
    }
}
