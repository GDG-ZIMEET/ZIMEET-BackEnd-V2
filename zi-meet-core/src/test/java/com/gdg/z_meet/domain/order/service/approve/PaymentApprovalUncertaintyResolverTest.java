package com.gdg.z_meet.domain.order.service.approve;

import com.gdg.z_meet.domain.order.client.KaKaoPayApiClient;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.service.recovery.PaymentRecoveryService;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentApprovalUncertaintyResolverTest {

    @Mock KaKaoPayApiClient apiClient;
    @Mock KakaoPayApproveTransactionService transactionService;
    @Mock PaymentRecoveryService recoveryService;

    private PaymentApprovalUncertaintyResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new PaymentApprovalUncertaintyResolver(apiClient, transactionService, recoveryService);
    }

    @Test
    void 상태조회에서_성공을_확인하면_UNKNOWN으로_바꾸지_않고_응답을_반환한다() {
        KaKaoPayApproveDTO.KaKaoApiResponse success =
                KaKaoPayApproveDTO.KaKaoApiResponse.builder().status("SUCCESS_PAYMENT").build();
        when(apiClient.inquirePaymentStatus("tid-1")).thenReturn(Optional.of(success));

        Optional<KaKaoPayApproveDTO.KaKaoApiResponse> result =
                resolver.recoverByInquiry(parameter(), payment(), "APPROVE_EMPTY_RESPONSE");

        assertThat(result).contains(success);
        verifyNoInteractions(transactionService, recoveryService);
    }

    @Test
    void 상태조회로도_확정할_수_없으면_UNKNOWN으로_전환하고_보상을_예약한다() {
        KakaoPayData payment = payment();
        when(apiClient.inquirePaymentStatus("tid-1")).thenReturn(Optional.empty());
        when(transactionService.findKakaoPayData("order-1")).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> resolver.recoverByInquiry(parameter(), payment, "APPROVE_EMPTY_RESPONSE"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", Code.INVALID_KAKAO_API_RESPONSE);

        verify(transactionService).updateStatus(10L, PaymentStatus.UNKNOWN);
        verify(recoveryService).scheduleRecovery("order-1", "tid-1", "APPROVE_EMPTY_RESPONSE");
    }

    private KaKaoPayApproveDTO.Parameter parameter() {
        return KaKaoPayApproveDTO.Parameter.builder()
                .orderId("order-1")
                .userId(1L)
                .pgToken("pg-token")
                .build();
    }

    private KakaoPayData payment() {
        return KakaoPayData.builder()
                .id(10L)
                .orderId("order-1")
                .tid("tid-1")
                .status(PaymentStatus.PROCESSING)
                .totalPrice(1200L)
                .build();
    }
}
