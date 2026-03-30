package com.gdg.z_meet.global.config.graceful;

import org.springframework.stereotype.Component;
import javax.annotation.PreDestroy;
import javax.sql.DataSource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShutdownOrderLogger {

    // 핵심!: 의존성을 주입받아 HikariCP 풀보다 반드시 먼저 실행(종료)되도록 Spring에게 힌트를 줌
    private final DataSource dataSource;

    @PreDestroy
    public void onShutdown() {
        log.info("=========================================");
        log.info("[Step 2 진입] 1단계(Tomcat 우아한 종료) 안전 완료!");
        log.info("[Step 2 진행] Spring Context 소멸 프로세스 시작");
        log.info("[Step 2 진행] HikariCP 커넥션 등 잉여 자원 안전 반납 중...");
        log.info("=========================================");
    }
}
