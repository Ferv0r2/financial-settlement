package com.financial.settlement.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
    "com.financial.settlement.api",
    "com.financial.settlement.common"
})
@EntityScan(basePackages = "com.financial.settlement.common")
@EnableJpaRepositories(basePackages = {
    "com.financial.settlement.api",
    "com.financial.settlement.common"
})
@EnableJpaAuditing
public class PayFlowApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayFlowApiApplication.class, args);
    }
}
