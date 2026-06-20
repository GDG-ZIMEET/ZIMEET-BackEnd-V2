package com.gdg.z_meet.domain.order.service.recovery;

import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.ledger.PaymentLedgerActorType;
import com.gdg.z_meet.domain.order.ledger.PaymentLedgerRecorder;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentRecoveryTransactionService {

    private final KakaoPayDataRepository repository;
    private final PaymentLedgerRecorder ledgerRecorder;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(Long paymentId) {
        LocalDateTime now = LocalDateTime.now();
        return repository.claimRecovery(paymentId, now, now.plusSeconds(30)) == 1;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public KakaoPayData find(Long paymentId) {
        return repository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(Code.PAYMENT_NOT_FOUND));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long paymentId) {
        KakaoPayData data = findManaged(paymentId);
        data.markRecoveryAsCompleted();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long paymentId) {
        KakaoPayData data = findManaged(paymentId);
        data.markRecoveryAsFailed();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean rescheduleOrExhaust(Long paymentId, int maxRetryCount) {
        KakaoPayData data = findManaged(paymentId);
        if (data.getRecoveryRetryCount() >= maxRetryCount) {
            data.markRecoveryAsFailed();
            if (data.getStatus() == PaymentStatus.PROCESSING) {
                PaymentStatus previousStatus = data.getStatus();
                data.setStatus(PaymentStatus.UNKNOWN);
                ledgerRecorder.record(
                        data, previousStatus, PaymentStatus.UNKNOWN,
                        PaymentLedgerActorType.BATCH, "payment-recovery",
                        "Payment recovery retry exhausted",
                        "recovery-unknown-" + data.getOrderId(),
                        "source=payment_recovery");
            }
            return true;
        }
        data.increaseRecoveryRetryCount();
        return false;
    }

    private KakaoPayData findManaged(Long paymentId) {
        return repository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(Code.PAYMENT_NOT_FOUND));
    }
}
