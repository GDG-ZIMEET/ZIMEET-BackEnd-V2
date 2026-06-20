package com.gdg.z_meet.domain.order.service.approve;

import com.gdg.z_meet.domain.order.client.KaKaoPayApiClient;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.service.idempotency.KakaoPayIdempotencyService;
import com.gdg.z_meet.domain.order.service.locking.KakaoPayLockService;
import com.gdg.z_meet.domain.order.service.recovery.PaymentRecoveryService;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KakaoPayApproveServiceTest {

    @Mock KaKaoPayApiClient apiClient;
    @Mock KakaoPayLockService lockService;
    @Mock KakaoPayApproveTransactionService transactionService;
    @Mock PaymentRecoveryService recoveryService;
    @Mock KakaoPayIdempotencyService idempotencyService;

    private KakaoPayApproveService service;

    @BeforeEach
    void setUp() {
        service = new KakaoPayApproveService(
                apiClient, lockService, transactionService, recoveryService, idempotencyService);
    }

    @Test
    void 워커_락_충돌은_결제실패가_아니므로_Recovery를_예약하지_않는다() {
        when(lockService.executeWithLock(eq("order-1"), any()))
                .thenThrow(new BusinessException(Code.IDEMPOTENCY_CONFLICT));

        assertThatThrownBy(() -> service.processApproval(parameter()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", Code.IDEMPOTENCY_CONFLICT);

        verifyNoInteractions(recoveryService);
    }

    @Test
    void 취소된_주문은_승인_접수를_다시_받지_않는다() {
        when(idempotencyService.validate(isNull(), anyString()))
                .thenReturn(KakaoPayIdempotencyService.IdempotencyValidationResult.empty());
        KakaoPayData cancelled = mock(KakaoPayData.class);
        when(cancelled.getStatus()).thenReturn(PaymentStatus.CANCELLED);
        when(transactionService.startPaymentProcessing(any())).thenReturn(cancelled);

        assertThatThrownBy(() -> service.approve(parameter(), null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", Code.INVALID_KAKAO_API_RESPONSE);
    }

    private KaKaoPayApproveDTO.Parameter parameter() {
        return KaKaoPayApproveDTO.Parameter.builder()
                .orderId("order-1").userId(1L).pgToken("pg-token").build();
    }
}
