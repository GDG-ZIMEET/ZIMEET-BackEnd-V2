package com.gdg.z_meet.domain.order.service;

import com.gdg.z_meet.domain.meeting.entity.Team;
import com.gdg.z_meet.domain.meeting.entity.UserTeam;
import com.gdg.z_meet.domain.meeting.repository.UserTeamRepository;
import com.gdg.z_meet.domain.order.entity.ProductType;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.entity.UserProfile;
import com.gdg.z_meet.domain.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 결제 승인 후 상품 처리 담당
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoItemProcessor {

    private final UserTeamRepository userTeamRepository;
    private final UserProfileRepository userProfileRepository;

    /**
     * 상품 유형에 따라 적절한 처리를 수행하고 필요한 정보를 반환
     */
    public ProcessResult processProduct(ProductType productType, Long totalPrice, User buyer) {
        
        if (productType == ProductType.TWO_TO_TWO || productType == ProductType.THREE_TO_THREE) {
            Team team = processTeamProduct(productType, totalPrice, buyer);
            return new ProcessResult(team, null);
            
        } else if (productType == ProductType.TICKET || productType == ProductType.SEASON) {
            UserProfile userProfile = processProfileProduct(productType, totalPrice, buyer);
            return new ProcessResult(null, userProfile);
        }
        
        return new ProcessResult(null, null);
    }

    /**
     * Hi 상품 처리 (TWO_TO_TWO, THREE_TO_THREE)
     */
    private Team processTeamProduct(ProductType productType, Long totalPrice, User buyer) {
        List<UserTeam> userTeams = userTeamRepository.findByUser(buyer);

        Team team = userTeams.stream()
                .map(UserTeam::getTeam)
                .filter(t -> t.getTeamType().name().equals(productType.name()))
                .findFirst()
                .orElse(null);

        if (team != null) {
            int increaseAmount = productType.calculateIncreaseAmount(totalPrice);
            team.increaseHi(increaseAmount);
            log.debug("Team Hi 증가 - teamId: {}, amount: {}", team.getId(), increaseAmount);
        }

        return team;
    }

    /**
     * Ticket/Season 상품 처리
     */
    private UserProfile processProfileProduct(ProductType productType, Long totalPrice, User buyer) {
        UserProfile userProfile = userProfileRepository.findByUser(buyer)
                .orElseThrow(() -> new RuntimeException("UserProfile not found"));

        if (productType == ProductType.TICKET) {
            int increaseAmount = productType.calculateIncreaseAmount(totalPrice);
            userProfile.increaseTicket(increaseAmount);
            log.debug("UserTicket 증가 - userId: {}, amount: {}", buyer.getId(), increaseAmount);
        } else { // SEASON
            userProfile.upgradeToPlus();
            userProfileRepository.save(userProfile);
            log.debug("UserProfile 업그레이드 (SEASON) - userId: {}", buyer.getId());
        }

        return userProfile;
    }

    /**
     * 처리 결과를 담는 불변 객체
     */
    public record ProcessResult(Team team, UserProfile userProfile) {}
}

