package com.gdg.z_meet.domain.order.service.approve;

import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.ledger.PaymentLedgerRecorder;
import com.gdg.z_meet.domain.order.repository.KakaoItemPurchaseRepository;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
import com.gdg.z_meet.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KakaoPayApproveTransactionServiceTest {

    @Mock KakaoPayDataRepository paymentRepository;
    @Mock KakaoItemPurchaseRepository purchaseRepository;
    @Mock ItemGrantService itemGrantService;
    @Mock PaymentLedgerRecorder ledgerRecorder;

    private KakaoPayApproveTransactionService service;

    @BeforeEach
    void setUp() {
        service = new KakaoPayApproveTransactionService(
                paymentRepository, purchaseRepository, itemGrantService, ledgerRecorder);
    }

    @Test
    void 최초_승인_요청만_PROCESSING을_선점하고_원장을_기록한다() {
        User buyer = mock(User.class);
        when(buyer.getId()).thenReturn(1L);
        KakaoPayData prepared = payment("order-1", buyer, PaymentStatus.PREPARED);
        KakaoPayData processing = payment("order-1", buyer, PaymentStatus.PROCESSING);
        when(paymentRepository.findByOrderId("order-1"))
                .thenReturn(Optional.of(prepared), Optional.of(processing), Optional.of(processing));
        when(paymentRepository.claimApproval("order-1", "pg-token")).thenReturn(1);

        KakaoPayData result = service.startPaymentProcessing(parameter());

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        verify(paymentRepository).claimApproval("order-1", "pg-token");
        verify(ledgerRecorder).record(eq(processing), eq(PaymentStatus.PREPARED),
                eq(PaymentStatus.PROCESSING), any(), anyString(), anyString(),
                eq("approve-start-order-1"), anyString());
    }

    @Test
    void 이미_PROCESSING이면_pgToken과_원장을_다시_쓰지_않는다() {
        User buyer = mock(User.class);
        when(buyer.getId()).thenReturn(1L);
        KakaoPayData processing = payment("order-1", buyer, PaymentStatus.PROCESSING);
        when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.of(processing));

        KakaoPayData result = service.startPaymentProcessing(parameter());

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        verify(paymentRepository, never()).claimApproval(anyString(), anyString());
        verifyNoInteractions(ledgerRecorder);
    }

    private KaKaoPayApproveDTO.Parameter parameter() {
        return KaKaoPayApproveDTO.Parameter.builder()
                .orderId("order-1").userId(1L).pgToken("pg-token").build();
    }

    private KakaoPayData payment(String orderId, User buyer, PaymentStatus status) {
        return KakaoPayData.builder().orderId(orderId).buyer(buyer).status(status).build();
    }
}
