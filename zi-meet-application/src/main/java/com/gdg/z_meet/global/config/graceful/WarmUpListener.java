package com.gdg.z_meet.global.config.graceful;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;

@Slf4j
@Component
@RequiredArgsConstructor
public class WarmUpListener {

    private final DataSource dataSource;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        log.info("=========================================");
        log.info("[Step 0] 헬스체크 대기 구간, HikariCP 커넥션 풀 예열 시작");
        try (Connection conn = dataSource.getConnection()) {
            log.info("[Step 0] DB 커넥션 체결 완료: {}", conn.getMetaData().getURL());
        } catch (Exception e) {
            log.error("DB 커넥션 예열 실패", e);
        }
        log.info("[Step 0] DB 예열 완료. 라우팅 전환 준비 끝!");
        log.info("=========================================");
    }
}
