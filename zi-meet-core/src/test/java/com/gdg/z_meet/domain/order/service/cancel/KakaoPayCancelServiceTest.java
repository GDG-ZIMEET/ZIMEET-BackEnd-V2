package com.gdg.z_meet.domain.order.service.cancel;

import com.gdg.z_meet.domain.order.client.KaKaoPayApiClient;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.dto.KaKaoPayCancelDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.service.idempotency.KakaoPayIdempotencyService;
import com.gdg.z_meet.domain.order.service.locking.KakaoPayLockService;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KakaoPayCancelServiceTest {

    @Mock KaKaoPayApiClient apiClient;
    @Mock KakaoPayCancelTransactionService transactionService;
    @Mock KakaoPayLockService lockService;
    @Mock KakaoPayIdempotencyService idempotencyService;

    private KakaoPayCancelService service;

    @BeforeEach
    void setUp() {
        service = new KakaoPayCancelService(apiClient, transactionService, lockService, idempotencyService);
        when(idempotencyService.validate(isNull(), anyString()))
                .thenReturn(KakaoPayIdempotencyService.IdempotencyValidationResult.empty());
        lenient().when(lockService.executeWithLock(anyString(), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
    }

    @Test
    void 이미_취소된_주문은_PG를_호출하지_않고_기존_결과를_반환한다() {
        KakaoPayData cancelled = payment(PaymentStatus.CANCELLED);
        when(transactionService.findPaymentData("order-1")).thenReturn(cancelled);

        KaKaoPayCancelDTO.Response response = service.cancel(parameter(), 1L, null);

        assertThat(response.getStatus()).isEqualTo("CANCEL_PAYMENT");
        verifyNoInteractions(apiClient);
    }

    @Test
    void 취소_응답이_유실되어도_PG_조회에서_취소를_확인하면_로컬을_완료한다() {
        KakaoPayData approved = payment(PaymentStatus.APPROVED);
        when(transactionService.findPaymentData("order-1")).thenReturn(approved);
        when(transactionService.validateAndGetPaymentData("order-1", 1L)).thenReturn(approved);
        when(apiClient.requestPaymentCancel(any())).thenReturn(Optional.empty());
        when(apiClient.inquirePaymentStatus("tid-1")).thenReturn(Optional.of(
                KaKaoPayApproveDTO.KaKaoApiResponse.builder().status("CANCEL_PAYMENT").build()));

        KaKaoPayCancelDTO.Response response = service.cancel(parameter(), 1L, null);

        assertThat(response.getStatus()).isEqualTo("CANCEL_PAYMENT");
        verify(transactionService).completeCancel(10L);
        verify(transactionService, never()).scheduleCancelRecovery(anyLong(), anyString());
    }

    @Test
    void 취소와_상태조회가_모두_불명확하면_보상을_예약하고_UNKNOWN_응답을_낸다() {
        KakaoPayData approved = payment(PaymentStatus.APPROVED);
        when(transactionService.findPaymentData("order-1")).thenReturn(approved);
        when(transactionService.validateAndGetPaymentData("order-1", 1L)).thenReturn(approved);
        when(apiClient.requestPaymentCancel(any())).thenReturn(Optional.empty());
        when(apiClient.inquirePaymentStatus("tid-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(parameter(), 1L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", Code.PAYMENT_UNKNOWN_STATUS_RETRY);
        verify(transactionService).scheduleCancelRecovery(10L, "CANCEL_TIMEOUT_UNKNOWN");
    }

    private KaKaoPayCancelDTO.Parameter parameter() {
        return KaKaoPayCancelDTO.Parameter.builder()
                .orderId("order-1").cancelReason("사용자 요청").build();
    }

    private KakaoPayData payment(PaymentStatus status) {
        User buyer = mock(User.class);
        when(buyer.getId()).thenReturn(1L);
        return KakaoPayData.builder()
                .id(10L).orderId("order-1").tid("tid-1")
                .buyer(buyer).status(status).totalPrice(1000L).build();
    }
}
