package com.gdg.z_meet.domain.order.ledger;

import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentLedgerImportService {

    private final KakaoPayDataRepository kakaoPayDataRepository;
    private final PaymentLedgerRepository ledgerRepository;
    private final PaymentLedgerAppendService appendService;

    @Transactional
    public ImportResult importExistingPayments() {
        long imported = 0;
        long skipped = 0;

        for (KakaoPayData payment : kakaoPayDataRepository.findAll()) {
            if (ledgerRepository.existsBySourcePaymentIdAndEventType(
                    payment.getId(), PaymentLedgerEventType.LEGACY_IMPORTED)) {
                skipped++;
                continue;
            }

            appendService.append(new PaymentLedgerAppendCommand(
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getTid(),
                    PaymentLedgerEventType.LEGACY_IMPORTED,
                    payment.getStatus() != null ? payment.getStatus() : PaymentStatus.UNKNOWN,
                    payment.getProductType(),
                    payment.getTotalPrice(),
                    payment.getBuyer() != null ? payment.getBuyer().getId() : null,
                    payment.getClub() != null ? payment.getClub().getId() : null,
                    PaymentLedgerActorType.REPLAY,
                    "local-import",
                    "Import existing KakaoPayData rows into append-only ledger",
                    "local-import-" + payment.getId(),
                    null,
                    "source=kakao_pay_data",
                    payment.getCreatedAt()
            ));
            imported++;
        }
        return new ImportResult(imported, skipped);
    }

    public record ImportResult(long importedCount, long skippedCount) {
    }
}
