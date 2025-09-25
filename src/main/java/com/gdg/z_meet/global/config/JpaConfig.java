package com.gdg.z_meet.global.config;

import com.gdg.z_meet.domain.chat.repository.mongo.MessageRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.gdg.z_meet.domain",
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, value = MessageRepository.class) // MongoDB 리포지토리 제외
)
public class JpaConfig {
    // JPA 관련 설정
}
