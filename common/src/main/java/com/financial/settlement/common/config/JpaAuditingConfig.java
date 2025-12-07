package com.financial.settlement.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 설정
 * createdAt, updatedAt 자동 관리
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
