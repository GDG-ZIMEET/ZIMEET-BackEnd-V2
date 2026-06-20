package com.gdg.z_meet.domain.order.service.ready;

import com.gdg.z_meet.domain.order.client.KaKaoPayApiClient;
import com.gdg.z_meet.domain.order.dto.KaKaoPayReadyDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.service.idempotency.KakaoPayIdempotencyService;
import com.gdg.z_meet.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KakaoPayReadyServiceTest {

    @Mock KaKaoPayApiClient apiClient;
    @Mock KakaoPayIdempotencyService idempotencyService;
    @Mock KakaoPayReadyTransactionService transactionService;

    private KakaoPayReadyService service;

    @BeforeEach
    void setUp() {
        service = new KakaoPayReadyService(apiClient, idempotencyService, transactionService);
    }

    @Test
    void Redis_응답이_없어도_DB에_완료된_Ready가_있으면_PG를_재호출하지_않는다() {
        KaKaoPayReadyDTO.Parameter parameter = parameter();
        User buyer = mock(User.class);
        KakaoPayData existing = mock(KakaoPayData.class);
        when(existing.getOrderId()).thenReturn("order-1");
        when(existing.getTid()).thenReturn("tid-1");
        when(existing.getReadyRedirectUrl()).thenReturn("https://redirect");
        when(idempotencyService.validate("1:READY:key-1", "10:TICKET:1000"))
                .thenReturn(KakaoPayIdempotencyService.IdempotencyValidationResult.processing("token-1"));
        when(transactionService.validateAndGetBuyer(parameter)).thenReturn(buyer);
        when(transactionService.reserveReadyData(eq(parameter), anyString(), eq(buyer),
                eq("1:READY:key-1"), eq("10:TICKET:1000")))
                .thenReturn(existing);

        KaKaoPayReadyDTO.Response response = service.ready(parameter, "key-1");

        assertThat(response.getOrderId()).isEqualTo("order-1");
        assertThat(response.getNextRedirectPcUrl()).isEqualTo("https://redirect");
        verifyNoInteractions(apiClient);
        verify(idempotencyService).unmarkAsProcessing("1:READY:key-1", "token-1");
    }

    @Test
    void 신규_Ready는_주문을_먼저_예약한_뒤_PG_응답을_확정한다() {
        KaKaoPayReadyDTO.Parameter parameter = parameter();
        User buyer = mock(User.class);
        KakaoPayData reservation = mock(KakaoPayData.class);
        when(reservation.getId()).thenReturn(10L);
        when(reservation.getOrderId()).thenReturn("order-1");
        when(idempotencyService.validate("1:READY:key-1", "10:TICKET:1000"))
                .thenReturn(KakaoPayIdempotencyService.IdempotencyValidationResult.processing("token-1"));
        when(transactionService.validateAndGetBuyer(parameter)).thenReturn(buyer);
        when(transactionService.reserveReadyData(eq(parameter), anyString(), eq(buyer),
                eq("1:READY:key-1"), eq("10:TICKET:1000")))
                .thenReturn(reservation);
        KaKaoPayReadyDTO.KakaoApiResponse pgResponse = KaKaoPayReadyDTO.KakaoApiResponse.builder()
                .tid("tid-1").next_redirect_pc_url("https://redirect").build();
        when(apiClient.requestPaymentReady(parameter, "order-1", buyer)).thenReturn(Optional.of(pgResponse));

        KaKaoPayReadyDTO.Response response = service.ready(parameter, "key-1");

        assertThat(response.getOrderId()).isEqualTo("order-1");
        verify(transactionService).completeReadyData(10L, pgResponse);
        verify(idempotencyService).cacheResponse(eq("1:READY:key-1"), any(KaKaoPayReadyDTO.Response.class));
    }

    private KaKaoPayReadyDTO.Parameter parameter() {
        return KaKaoPayReadyDTO.Parameter.builder()
                .buyerId(1L).teamId(10L).productType("TICKET").totalPrice(1000L).vat(90L).build();
    }
}
