package com.financial.settlement.common.repository;

import com.financial.settlement.common.domain.Merchant;
import com.financial.settlement.common.domain.MerchantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MerchantRepository extends JpaRepository<Merchant, String> {

    /**
     * 사업자번호로 가맹점 조회
     */
    Optional<Merchant> findByBusinessNumber(String businessNumber);

    /**
     * 상태별 가맹점 목록 조회
     */
    List<Merchant> findByStatus(MerchantStatus status);

    /**
     * 활성 가맹점 목록 조회
     */
    default List<Merchant> findAllActive() {
        return findByStatus(MerchantStatus.ACTIVE);
    }

    /**
     * 사업자번호 존재 여부 확인
     */
    boolean existsByBusinessNumber(String businessNumber);
}
