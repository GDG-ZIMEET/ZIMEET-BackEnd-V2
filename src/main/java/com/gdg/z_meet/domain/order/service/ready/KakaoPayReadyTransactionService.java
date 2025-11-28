package com.gdg.z_meet.domain.order.service.ready;

import com.gdg.z_meet.domain.meeting.repository.UserTeamRepository;
import com.gdg.z_meet.domain.order.converter.KaKaoPayReadyConverter;
import com.gdg.z_meet.domain.order.dto.KaKaoPayReadyDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.ProductType;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoPayReadyTransactionService {

    private final UserRepository userRepository;
    private final UserTeamRepository userTeamRepository;
    private final KakaoPayDataRepository kakaoPayDataRepository;

    /**
     * 주문자 검증 및 조회
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public User validateAndGetBuyer(KaKaoPayReadyDTO.Parameter parameter) {
        // 1. 주문자 조회
        User buyer = userRepository.findById(parameter.getBuyerId())
                .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));

        // 2. 상품 타입 검증
        validateProductType(parameter);

        return buyer;
    }

    /**
     * 결제 준비 데이터 저장
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveReadyData(KaKaoPayReadyDTO.KakaoApiResponse kakaoApiResponse,
            KaKaoPayReadyDTO.Parameter parameter,
            String orderId,
            User buyer) {
        KakaoPayData kaKaoPayData = KaKaoPayReadyConverter.toKakaoPayData(
                kakaoApiResponse, parameter, orderId, buyer);
        kakaoPayDataRepository.save(kaKaoPayData);
        log.debug("결제 준비 데이터 저장 완료 - orderId: {}", orderId);
    }

    private void validateProductType(KaKaoPayReadyDTO.Parameter parameter) {
        if (!ProductType.isValid(parameter.getProductType())) {
            throw new BusinessException(Code.INVALID_PRODUCT_TYPE);
        }

        ProductType productType = ProductType.valueOf(parameter.getProductType());

        // TICKET, SEASON이 아닌 경우 (즉, TWO_TO_TWO, THREE_TO_THREE인 경우) 팀 멤버 검증
        if (productType != ProductType.TICKET && productType != ProductType.SEASON) {
            boolean isMember = userTeamRepository.existsByUserIdAndTeamIdAndActiveStatus(
                    parameter.getBuyerId(), parameter.getTeamId());
            if (!isMember) {
                throw new BusinessException(Code.TEAM_USER_NOT_FOUND);
            }
        }
    }
}
