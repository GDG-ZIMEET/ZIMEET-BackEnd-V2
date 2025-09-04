package com.gdg.z_meet.domain.order.service;

import com.gdg.z_meet.domain.meeting.entity.Team;
import com.gdg.z_meet.domain.meeting.entity.UserTeam;
import com.gdg.z_meet.domain.meeting.repository.UserTeamRepository;
import com.gdg.z_meet.domain.order.client.KaKaoPayApiClient;
import com.gdg.z_meet.domain.order.converter.KaKaoPayApproveConverter;
import com.gdg.z_meet.domain.order.converter.KaKaoPayReadyConverter;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.dto.KaKaoPayReadyDTO;
import com.gdg.z_meet.domain.order.entity.ItemPurchase;
import com.gdg.z_meet.domain.order.entity.KaKaoPayData;
import com.gdg.z_meet.domain.order.entity.ProductType;
import com.gdg.z_meet.domain.order.repository.ItemPurchaseRepository;
import com.gdg.z_meet.domain.order.repository.KaKaoPayDataRepository;
import com.gdg.z_meet.domain.order.repository.NamedLockRepository;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.entity.UserProfile;
import com.gdg.z_meet.domain.user.repository.UserProfileRepository;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KaKaoPayService {

    private final UserRepository userRepository;
    private final KaKaoPayApiClient kaKaoPayApiClient;
    private final KaKaoPayDataRepository kakaoPayDataRepository;
    private final UserTeamRepository userTeamRepository;
    private final ItemPurchaseRepository itemPurchaseRepository;
    private final UserProfileRepository userProfileRepository;
    private final NamedLockRepository namedLockRepository;

    @Transactional
    public KaKaoPayReadyDTO.Response KaKaoPayReady(KaKaoPayReadyDTO.Parameter parameter) {

        // 1. 주문자 정보 및 결제할 상품 검증
        User buyer = userRepository.findById(parameter.getBuyerId()).orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));

        if (!ProductType.valueOf(parameter.getProductType()).equals(ProductType.TICKET) && !ProductType.valueOf(parameter.getProductType()).equals(ProductType.SEASON)) {
                boolean isMember = userTeamRepository.existsByUserIdAndTeamIdAndActiveStatus(parameter.getBuyerId(), parameter.getTeamId());
            if (!isMember) {
                throw new BusinessException(Code.TEAM_USER_NOT_FOUND);
            }
        }

        if (!ProductType.isValid(parameter.getProductType())) { throw new BusinessException(Code.INVALID_PRODUCT_TYPE);}

        /**
         * tid                     // 카카오페이 측 결제 고유 번호
         * next_redirect_pc_url    // 카카오페이가 생성한 결제 경로, 사용자는 해당 경로로 이동하여 결제 진행
         */
        // 2. 결제 준비 API 호출 (주문 ID 할당)
        String orderId = createOrderId();

        KaKaoPayReadyDTO.KakaoApiResponse kakaoApiResponse = Optional.ofNullable(
                kaKaoPayApiClient.requestPaymentReady(parameter, orderId, buyer)).orElseThrow(()-> new BusinessException(Code.KAKAO_API_RESPONSE_ERROR));

        log.info("KaKaoPay 결제 준비 성공. 주문 ID : {}", orderId);

        if (kakaoApiResponse.getTid() == null || kakaoApiResponse.getNext_redirect_pc_url() == null) {throw new BusinessException(Code.INVALID_KAKAO_API_RESPONSE);}

        // 3. 결제 정보 DB 저장
        KaKaoPayData kaKaoPayData = KaKaoPayReadyConverter.toKakaoPayData(kakaoApiResponse, parameter, orderId, buyer);
        kakaoPayDataRepository.save(kaKaoPayData);

        return KaKaoPayReadyConverter.toResponse(kakaoApiResponse, orderId);
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KaKaoPayApproveDTO.Response KaKaoPayApprove(KaKaoPayApproveDTO.Parameter parameter) {

        String lockName = "LOCK_KAKAO_PAY_APPROVE_" + parameter.getOrderId();

        try {
            acquireLock(lockName);     // 네임드 락 할당

            // 1. 결제 정보 조회 및 검증
            KaKaoPayData kakaoPayData = kakaoPayDataRepository.findByOrderId(parameter.getOrderId()).orElseThrow(() -> new BusinessException(Code.PAYMENT_NOT_FOUND));
            if (!kakaoPayData.getBuyer().getId().equals(parameter.getUserId())) { throw new BusinessException(Code.KAKAO_API_INVALID_BUYER);}

            // 2. 최종 승인 API 호출
            KaKaoPayApproveDTO.KaKaoApiResponse kakaoApiResponse = Optional.ofNullable(
                    kaKaoPayApiClient.requestPaymentApprove(parameter, kakaoPayData)).orElseThrow(() -> new BusinessException(Code.INVALID_KAKAO_API_RESPONSE));
            log.info("카카오페이 결제 최종 승인 성공");

            ProductType productType = kakaoPayData.getProductType();
            Long totalPrice = kakaoPayData.getTotalPrice();
            User buyer = kakaoPayData.getBuyer();

            Team team = null;
            UserProfile userProfile = null;

            // 3. 결제 내역 생성
            if (productType == ProductType.TWO_TO_TWO || productType == ProductType.THREE_TO_THREE) {
                List<UserTeam> userTeams = userTeamRepository.findByUser(kakaoPayData.getBuyer());

                Optional<Team> matchingTeam = userTeams.stream()
                        .map(UserTeam::getTeam)
                        .filter(teams -> teams.getTeamType().name().equals(kakaoPayData.getProductType().name()))
                        .findFirst();

                if (matchingTeam.isPresent()) { team = matchingTeam.get(); }

                if (team != null) {
                    int increaseAmount = productType.getPriceMap().entrySet().stream()
                            .filter(entry -> entry.getValue().equals(totalPrice.intValue()))
                            .map(Map.Entry::getKey)
                            .findFirst()
                            .orElseThrow(() -> new BusinessException(Code.INVALID_TOTAL_PRICE));

                    team.increaseHi(increaseAmount);  // hi 증가
                }
            }

            else if (productType == ProductType.TICKET || productType == ProductType.SEASON) {
                Optional<UserProfile> userProfileOptional = userProfileRepository.findByUser(kakaoPayData.getBuyer());

                if (userProfileOptional.isPresent()) {
                    userProfile = userProfileOptional.get();

                    if (productType == ProductType.TICKET) {
                        int increaseAmount = productType.getPriceMap().entrySet().stream()
                                .filter(entry -> entry.getValue().equals(totalPrice.intValue()))
                                .map(Map.Entry::getKey)
                                .findFirst()
                                .orElseThrow(() -> new BusinessException(Code.INVALID_TOTAL_PRICE));

                        userProfile.increaseTicket(increaseAmount);
                    } else { // 시즌 권
                        userProfile.upgradeToPlus();
                        userProfileRepository.save(userProfile);
                    }
                } else {
                    throw new BusinessException(Code.USER_PROFILE_NOT_FOUND);
                }
            }

            ItemPurchase itemPurchase = KaKaoPayApproveConverter.toItemPurchase(kakaoApiResponse, kakaoPayData, buyer, team, userProfile);
            itemPurchaseRepository.save(itemPurchase);

            kakaoPayDataRepository.delete(kakaoPayData);

            return KaKaoPayApproveConverter.toResponse(kakaoApiResponse, parameter.getOrderId());
        }finally {
            releaseLock(lockName);     // 네임드 락 해제
        }
    }

    private String createOrderId() {
        return UUID.randomUUID().toString();
    }


    private void acquireLock(String lockName) {
        log.info("락 요청 lockName : {}", lockName);
        Integer result = namedLockRepository.getLock(lockName);

        if (result == null || result == 0) { throw new BusinessException(Code.INTERNAL_SERVER_ERROR); }
    }

    private void releaseLock(String lockName) {
        log.info("락 반환 lockName : {}", lockName);
        namedLockRepository.releaseLock(lockName);
    }

}
