package com.gdg.z_meet.domain.order.service;

import com.gdg.z_meet.domain.order.service.monitoring.KakaoPayLockMonitoringService;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 결제 프로세스 락 관리 (MySQL Named Lock)
 *
 * [개선 사항]
 * - DataSource를 직접 사용하여 락 전용 Connection 관리
 * - 비즈니스 트랜잭션과 락 점유 Connection 분리
 * - executeWithLock 패턴으로 락 획득-실행-해제 라이프사이클 명시적 제어
 */
@Slf4j
@Service
public class KakaoPayLockService {

    private final DataSource dataSource;
    private final KakaoPayLockMonitoringService kakaoPaylockMonitoringService;

    public KakaoPayLockService(@Qualifier("lockDataSource") DataSource dataSource,
            KakaoPayLockMonitoringService kakaoPaylockMonitoringService) {
        this.dataSource = dataSource;
        this.kakaoPaylockMonitoringService = kakaoPaylockMonitoringService;
    }

    private static final String LOCK_PREFIX = "LOCK_KAKAO_PAY_APPROVE_";
    private static final String GET_LOCK_QUERY = "SELECT GET_LOCK(?, ?)";
    private static final String RELEASE_LOCK_QUERY = "SELECT RELEASE_LOCK(?)";
    private static final int LOCK_TIMEOUT_SECONDS = 3;

    /**
     * 네임드 락을 획득하고 비즈니스 로직을 수행한 뒤 락을 해제함
     */
    public <T> T executeWithLock(String orderId, Supplier<T> businessLogic) {
        String lockName = LOCK_PREFIX + orderId;
        String ownerId = UUID.randomUUID().toString();
        Instant start = Instant.now();

        try (Connection conn = dataSource.getConnection()) {

            if (!acquireLock(conn, lockName, ownerId, start)) {
                throw new BusinessException(Code.IDEMPOTENCY_CONFLICT);
            }

            try {
                return businessLogic.get();
            } finally {
                releaseLock(conn, lockName, ownerId, start);
            }

        } catch (SQLException e) {
            kakaoPaylockMonitoringService.failed(lockName, ownerId, "DB_CONNECTION_ERROR: " + e.getMessage());
            log.error("네임드 락 실행 중 DB 오류 - lockName: {}", lockName, e);
            throw new BusinessException(Code.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean acquireLock(Connection conn, String lockName, String ownerId, Instant start) {
        try (PreparedStatement pstmt = conn.prepareStatement(GET_LOCK_QUERY)) {
            pstmt.setString(1, lockName);
            pstmt.setInt(2, LOCK_TIMEOUT_SECONDS);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int result = rs.getInt(1);
                    int waitMs = (int) Duration.between(start, Instant.now()).toMillis();

                    if (result == 1) {
                        kakaoPaylockMonitoringService.acquired(lockName, ownerId, Instant.now(), waitMs);
                        log.debug("네임드락 획득 성공 - lockName: {}, ownerId: {}", lockName, ownerId);
                        return true;
                    } else {
                        kakaoPaylockMonitoringService.timeout(lockName, ownerId, waitMs);
                        log.warn("네임드락 획득 실패 (타임아웃) - lockName: {}", lockName);
                        return false;
                    }
                }
            }
        } catch (SQLException e) {
            kakaoPaylockMonitoringService.failed(lockName, ownerId, "ACQUIRE_FAILED: " + e.getMessage());
            log.error("네임드락 획득 중 오류 - lockName: {}", lockName, e);
        }
        return false;
    }

    private void releaseLock(Connection conn, String lockName, String ownerId, Instant acquiredAt) {
        try (PreparedStatement pstmt = conn.prepareStatement(RELEASE_LOCK_QUERY)) {
            pstmt.setString(1, lockName);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int result = rs.getInt(1);
                    int holdMs = (int) Duration.between(acquiredAt, Instant.now()).toMillis();

                    if (result == 1) {
                        kakaoPaylockMonitoringService.released(lockName, ownerId, Instant.now(), holdMs);
                        log.debug("네임드락 해제 성공 - lockName: {}", lockName);
                    } else {
                        // 락이 존재하지 않거나 내 소유가 아님
                        kakaoPaylockMonitoringService.failed(lockName, ownerId, "RELEASE_FAILED_NOT_OWNER");
                        log.warn("네임드락 해제 실패 (소유자 불일치 등) - lockName: {}", lockName);
                    }
                }
            }
        } catch (SQLException e) {
            kakaoPaylockMonitoringService.failed(lockName, ownerId, "RELEASE_ERROR: " + e.getMessage());
            log.error("네임드락 해제 중 오류 - lockName: {}", lockName, e);
        }
    }
}