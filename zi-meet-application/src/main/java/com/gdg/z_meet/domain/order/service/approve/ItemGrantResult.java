package com.gdg.z_meet.domain.order.service.approve;

import com.gdg.z_meet.domain.meeting.entity.Team;
import com.gdg.z_meet.domain.user.entity.UserProfile;

/**
 * 아이템 지급 처리 결과 VO.
 * <p>
 * 구매 내역(ItemPurchase) 생성에 필요한 최소한의 도메인 객체만 포함합니다.
 * team 또는 userProfile 중 하나만 존재할 수 있습니다 (ProductType에 따라 결정).
 * </p>
 *
 * @param team        지급된 팀 (TWO_TO_TWO, THREE_TO_THREE 상품인 경우)
 * @param userProfile 지급된 유저 프로필 (TICKET, SEASON 상품인 경우)
 */
public record ItemGrantResult(Team team, UserProfile userProfile) {
}
