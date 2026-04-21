package com.gdg.z_meet.global.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import javax.sql.DataSource;

@Slf4j
@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.hikari.maximum-pool-size:100}")
    private int mainPoolSize;

    @Value("${spring.datasource-lock.hikari.maximum-pool-size:20}")
    private int lockPoolSize;

    @Primary
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource")
    public HikariDataSource dataSource() {
        HikariDataSource ds = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
        ds.setMaximumPoolSize(mainPoolSize);
        ds.setPoolName("HikariCP-Business");
        return ds;
    }

    @Bean(name = "lockDataSource")
    @ConfigurationProperties(prefix = "spring.datasource-lock")
    public HikariDataSource lockDataSource() {
        HikariDataSource ds = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
        ds.setMaximumPoolSize(lockPoolSize);
        ds.setPoolName("HikariCP-Lock");
        return ds;
    }

    @Bean
    public CommandLineRunner logPoolSize(
            DataSource dataSource,
            @Qualifier("lockDataSource") DataSource lockDataSource) {
        return args -> {
            if (dataSource instanceof HikariDataSource ds) {
                log.info("[Pool Config] Main Business Pool Size: {}", ds.getMaximumPoolSize());
            }
            if (lockDataSource instanceof HikariDataSource ds) {
                log.info("[Pool Config] Lock Pool Size: {}", ds.getMaximumPoolSize());
            }
        };
    }
}