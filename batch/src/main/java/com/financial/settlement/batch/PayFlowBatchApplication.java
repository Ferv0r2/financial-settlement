package com.financial.settlement.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
    "com.financial.settlement.batch",
    "com.financial.settlement.common"
})
@EntityScan(basePackages = "com.financial.settlement.common")
@EnableJpaRepositories(basePackages = {
    "com.financial.settlement.batch",
    "com.financial.settlement.common"
})
public class PayFlowBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayFlowBatchApplication.class, args);
    }
}
