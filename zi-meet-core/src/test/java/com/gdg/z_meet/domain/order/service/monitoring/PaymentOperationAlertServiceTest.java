package com.gdg.z_meet.domain.order.service.monitoring;

import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.KakaoPayData.RecoveryStatus;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentOperationAlertServiceTest {

    @Mock KakaoPayDataRepository repository;

    private PaymentOperationAlertService service;

    @BeforeEach
    void setUp() {
        service = new PaymentOperationAlertService(repository);
    }

    @Test
    void UNKNOWN과_복구_주의상태를_운영_확인대상으로_조회한다() {
        PageRequest pageable = PageRequest.of(0, 10);
        KakaoPayData unknown = KakaoPayData.builder()
                .id(10L)
                .orderId("order-1")
                .tid("tid-1")
                .status(PaymentStatus.UNKNOWN)
                .totalPrice(1200L)
                .build();
        when(repository.findOperationalAttentionTargets(
                eq(PaymentStatus.UNKNOWN),
                eq(List.of(RecoveryStatus.PENDING, RecoveryStatus.PROCESSING, RecoveryStatus.FAILED)),
                eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(unknown), pageable, 1));

        var result = service.findAttentionTargets(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).orderId()).isEqualTo("order-1");
        verify(repository).findOperationalAttentionTargets(
                PaymentStatus.UNKNOWN,
                List.of(RecoveryStatus.PENDING, RecoveryStatus.PROCESSING, RecoveryStatus.FAILED),
                pageable);
    }
}
