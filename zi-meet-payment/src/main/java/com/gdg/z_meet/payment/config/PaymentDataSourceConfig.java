package com.gdg.z_meet.payment.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 결제 서버 전용 DataSource.
 *
 * payment-db 는 Core 의 business-db 와 물리적으로 분리된 MySQL 인스턴스이다.
 * 결제 승인 트랜잭션과 원장 append, Outbox 저장은 모두 이 DataSource 안에서 원자적으로 수행된다.
 * Core 쪽 엔티티(User, Club 등)를 참조해야 할 경우 REST 또는 이벤트로만 가져온다 -- 크로스 DB 조인 금지.
 */
@Configuration
public class PaymentDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties paymentDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource paymentDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }
}
