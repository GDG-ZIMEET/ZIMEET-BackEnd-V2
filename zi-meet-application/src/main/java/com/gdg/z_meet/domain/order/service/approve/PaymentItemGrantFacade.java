package com.gdg.z_meet.domain.order.service.approve;

import com.gdg.z_meet.domain.meeting.entity.Team;
import com.gdg.z_meet.domain.meeting.entity.UserTeam;
import com.gdg.z_meet.domain.meeting.repository.UserTeamRepository;
import com.gdg.z_meet.domain.order.entity.enums.ProductType;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.entity.UserProfile;
import com.gdg.z_meet.domain.user.repository.UserProfileRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 결제 완료 후 아이템 지급을 담당하는 Facade 구현체.
 * <p>
 * 역할:
 * <ul>
 * <li>ProductType 분기 라우팅 (어떤 상품을 어느 도메인 서비스로 위임할지 결정)</li>
 * <li>기존 버그 수정: 팀을 찾지 못할 경우 {@link BusinessException}을 던져 결제 롤백 유도</li>
 * <li>Order 도메인과 User/Meeting 도메인 사이의 경계 유지
 * (OrderService는 이 Facade의 {@link ItemGrantService} 인터페이스만 알고 있음)</li>
 * </ul>
 * </p>
 *
 * <p>
 * <strong>[설계 원칙]</strong>
 * </p>
 * <ul>
 * <li>이 클래스는 아이템 지급의 <em>조율(Orchestration)만</em> 담당합니다.</li>
 * <li>구체적인 도메인 규칙(Hi 잔액 계산, Ticket 상한 등)은 각 도메인 엔티티/서비스에 위임합니다.</li>
 * <li>여기서 예외가 발생하면 상위 트랜잭션이 롤백되어 카카오페이 환불 스케줄러가 동작합니다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentItemGrantFacade implements ItemGrantService {

    private final UserTeamRepository userTeamRepository;
    private final UserProfileRepository userProfileRepository;

    /**
     * {@inheritDoc}
     *
     * <p>
     * <strong>ProductType별 처리 흐름:</strong>
     * </p>
     * <ul>
     * <li>TWO_TO_TWO / THREE_TO_THREE → {@link #grantTeamHi} : 팀 Hi 충전</li>
     * <li>TICKET / SEASON → {@link #grantProfileProduct} : 개인 티켓 충전 또는 플러스
     * 업그레이드</li>
     * </ul>
     *
     * @throws BusinessException {@link Code#MY_TEAM_NOT_FOUND} 팀 상품 구매자에게 활성 팀이 없을
     *                           때
     * @throws BusinessException {@link Code#PROFILE_NOT_FOUND} 프로필 상품 구매자의 프로필이 없을
     *                           때
     */
    @Override
    @Transactional
    public ItemGrantResult grant(ProductType productType, Long totalPrice, User buyer) {
        log.info("[ItemGrant] 아이템 지급 시작 - userId: {}, productType: {}, totalPrice: {}",
                buyer.getId(), productType, totalPrice);

        return switch (productType) {
            case TWO_TO_TWO, THREE_TO_THREE -> grantTeamHi(productType, totalPrice, buyer);
            case TICKET, SEASON -> grantProfileProduct(productType, totalPrice, buyer);
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private: 팀 상품 (Hi 충전)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 구매자의 활성 팀 중 ProductType과 일치하는 팀에게 Hi를 충전합니다.
     *
     * <p>
     * <strong>[기존 버그 수정]</strong>: 기존 KakaoItemProcessor는 팀을 찾지 못하면
     * 조용히 null을 반환하고 종료했습니다. 이 경우 카카오페이 과금은 완료되었지만
     * 실제 아이템은 지급되지 않는 치명적인 데이터 불일치가 발생합니다.
     * 수정된 코드는 팀을 찾지 못했을 때 {@link BusinessException}을 던져
     * 상위 트랜잭션을 롤백하고, 보상 스케줄러가 카카오페이 환불을 수행하도록 개선했습니다.
     * </p>
     */
    private ItemGrantResult grantTeamHi(ProductType productType, Long totalPrice, User buyer) {
        List<UserTeam> userTeams = userTeamRepository.findByUser(buyer);

        Team targetTeam = userTeams.stream()
                .map(UserTeam::getTeam)
                .filter(team -> team.getTeamType().name().equals(productType.name()))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("[ItemGrant] 팀 Hi 충전 실패: 활성 팀 없음 - userId: {}, productType: {}",
                            buyer.getId(), productType);
                    // ✅ BUG FIX: 기존에는 null 반환으로 아이템 미지급 묵살 → 이제 예외로 롤백 유도
                    return new BusinessException(Code.MY_TEAM_NOT_FOUND);
                });

        int increaseAmount = productType.calculateIncreaseAmount(totalPrice);
        targetTeam.increaseHi(increaseAmount);

        log.info("[ItemGrant] 팀 Hi 지급 완료 - teamId: {}, amount: +{}", targetTeam.getId(), increaseAmount);
        return new ItemGrantResult(targetTeam, null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private: 개인 상품 (Ticket, Season)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * TICKET: 유저 프로필의 티켓을 증가시킵니다.
     * SEASON: 유저 프로필의 레벨을 PLUS로 업그레이드합니다.
     */
    private ItemGrantResult grantProfileProduct(ProductType productType, Long totalPrice, User buyer) {
        UserProfile userProfile = userProfileRepository.findByUser(buyer)
                .orElseThrow(() -> {
                    log.error("[ItemGrant] 프로필 상품 지급 실패: 프로필 없음 - userId: {}", buyer.getId());
                    return new BusinessException(Code.PROFILE_NOT_FOUND);
                });

        if (productType == ProductType.TICKET) {
            int increaseAmount = productType.calculateIncreaseAmount(totalPrice);
            userProfile.increaseTicket(increaseAmount);
            log.info("[ItemGrant] 티켓 지급 완료 - userId: {}, amount: +{}", buyer.getId(), increaseAmount);
        } else { // SEASON
            userProfile.upgradeToPlus();
            log.info("[ItemGrant] 시즌권(PLUS) 업그레이드 완료 - userId: {}", buyer.getId());
        }

        return new ItemGrantResult(null, userProfile);
    }
}
