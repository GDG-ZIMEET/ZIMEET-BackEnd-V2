package com.gdg.z_meet.domain.order.service.locking;

import com.gdg.z_meet.domain.order.service.monitoring.KakaoPayLockMonitoringService;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.DatabaseMetaData;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KakaoPayLockServiceTest {

    @Mock
    private DataSource lockDataSource;
    @Mock
    private KakaoPayLockMonitoringService lockMonitoringService;
    @Mock
    private Connection connection;
    @Mock
    private PreparedStatement preparedStatement;
    @Mock
    private ResultSet resultSet;
    @Mock
    private DatabaseMetaData databaseMetaData;

    private KakaoPayLockService kakaoPayLockService;

    @BeforeEach
    void setUp() throws SQLException {
        kakaoPayLockService = new KakaoPayLockService(lockDataSource, lockMonitoringService);
        lenient().when(connection.getMetaData()).thenReturn(databaseMetaData);
        lenient().when(databaseMetaData.getDatabaseProductName()).thenReturn("MySQL");
    }

    @Test
    @DisplayName("락 획득 성공 시 비즈니스 로직을 실행하고 락을 해제하며 커넥션을 닫는다")
    void executeWithLock_Success() throws SQLException {
        // given
        String orderId = "order-123";
        String expectedResult = "success";
        Supplier<String> businessLogic = () -> expectedResult;

        // Mocking JDBC interactions
        given(lockDataSource.getConnection()).willReturn(connection);
        given(connection.prepareStatement(anyString())).willReturn(preparedStatement);
        given(preparedStatement.executeQuery()).willReturn(resultSet);
        given(resultSet.next()).willReturn(true);
        given(resultSet.getInt(1)).willReturn(1); // GET_LOCK success

        // when
        String result = kakaoPayLockService.executeWithLock(orderId, businessLogic);

        // then
        assertThat(result).isEqualTo(expectedResult);

        // 1. 커넥션 획득 확인
        verify(lockDataSource).getConnection();

        // 2. GET_LOCK 쿼리 실행 확인
        verify(connection).prepareStatement(contains("GET_LOCK"));

        // 3. RELEASE_LOCK 쿼리 실행 확인 (finally 블록)
        verify(connection).prepareStatement(contains("RELEASE_LOCK"));

        // 4. 커넥션 종료 확인 (try-with-resources)
        verify(connection).close();

        // 5. 모니터링 서비스 호출 확인
        verify(lockMonitoringService).acquired(anyString(), anyString(), any(), anyInt());
        verify(lockMonitoringService).released(anyString(), anyString(), any(), anyInt());
    }

    @Test
    @DisplayName("락 획득 실패(결과 0) 시 IDEMPOTENCY_CONFLICT 예외를 던지고 로직을 실행하지 않는다")
    void executeWithLock_Fail_LockAcquisition() throws SQLException {
        // given
        String orderId = "order-123";
        Supplier<String> businessLogic = () -> "should not run";

        given(lockDataSource.getConnection()).willReturn(connection);
        given(connection.prepareStatement(anyString())).willReturn(preparedStatement);
        given(preparedStatement.executeQuery()).willReturn(resultSet);
        given(resultSet.next()).willReturn(true);
        given(resultSet.getInt(1)).willReturn(0); // GET_LOCK fail (0)

        // when & then
        assertThatThrownBy(() -> kakaoPayLockService.executeWithLock(orderId, businessLogic))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", Code.IDEMPOTENCY_CONFLICT);

        // 로직 실행 안 됨
        // 락 해제 시도 안 함 (획득 못했으므로)
        verify(connection, never()).prepareStatement(contains("RELEASE_LOCK"));

        // 하지만 커넥션은 닫혀야 함
        verify(connection).close();
    }

    @Test
    @DisplayName("비즈니스 로직 실행 중 예외가 발생해도 락을 해제하고 커넥션을 닫는다")
    void executeWithLock_BusinessLogicException() throws SQLException {
        // given
        String orderId = "order-123";
        RuntimeException businessException = new RuntimeException("Business Error");
        Supplier<String> businessLogic = () -> {
            throw businessException;
        };

        given(lockDataSource.getConnection()).willReturn(connection);
        given(connection.prepareStatement(anyString())).willReturn(preparedStatement);
        given(preparedStatement.executeQuery()).willReturn(resultSet);
        given(resultSet.next()).willReturn(true);
        given(resultSet.getInt(1)).willReturn(1); // GET_LOCK success

        // when & then
        assertThatThrownBy(() -> kakaoPayLockService.executeWithLock(orderId, businessLogic))
                .isSameAs(businessException);

        // 락 해제는 반드시 수행되어야 함
        verify(connection).prepareStatement(contains("RELEASE_LOCK"));

        // 커넥션도 닫혀야 함
        verify(connection).close();

        // 모니터링: 획득은 했으므로 acquired는 호출됨
        verify(lockMonitoringService).acquired(anyString(), anyString(), any(), anyInt());
        verify(lockMonitoringService).released(anyString(), anyString(), any(), anyInt());
    }

    @Test
    @DisplayName("DB 연결 오류 시 INTERNAL_SERVER_ERROR 예외를 던지고 모니터링에 실패를 기록한다")
    void executeWithLock_SQLException() throws SQLException {
        // given
        String orderId = "order-123";
        Supplier<String> businessLogic = () -> "success";
        SQLException sqlException = new SQLException("Connection failed");

        given(lockDataSource.getConnection()).willThrow(sqlException);

        // when & then
        assertThatThrownBy(() -> kakaoPayLockService.executeWithLock(orderId, businessLogic))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", Code.INTERNAL_SERVER_ERROR);

        // 모니터링 실패 기록 확인
        verify(lockMonitoringService).failed(anyString(), anyString(), anyString());
    }
}
