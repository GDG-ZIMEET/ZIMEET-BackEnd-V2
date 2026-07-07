package com.gdg.z_meet.domain.order.service.monitoring;

import com.gdg.z_meet.domain.order.dto.PaymentOperationAlertResponse;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.KakaoPayData.RecoveryStatus;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentOperationAlertService {

    private static final List<RecoveryStatus> ATTENTION_RECOVERY_STATUSES =
            List.of(RecoveryStatus.PENDING, RecoveryStatus.PROCESSING, RecoveryStatus.FAILED);

    private final KakaoPayDataRepository kakaoPayDataRepository;

    @Transactional(readOnly = true)
    public Page<PaymentOperationAlertResponse> findAttentionTargets(Pageable pageable) {
        return kakaoPayDataRepository
                .findOperationalAttentionTargets(PaymentStatus.UNKNOWN, ATTENTION_RECOVERY_STATUSES, pageable)
                .map(PaymentOperationAlertResponse::from);
    }

    @Transactional(readOnly = true)
    public long countAttentionTargets() {
        Page<KakaoPayData> firstPage = kakaoPayDataRepository.findOperationalAttentionTargets(
                PaymentStatus.UNKNOWN, ATTENTION_RECOVERY_STATUSES, Pageable.ofSize(1));
        return firstPage.getTotalElements();
    }
}
