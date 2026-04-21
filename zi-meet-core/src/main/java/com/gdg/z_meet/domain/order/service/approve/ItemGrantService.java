package com.gdg.z_meet.domain.order.service.approve;

import com.gdg.z_meet.domain.order.entity.enums.ProductType;
import com.gdg.z_meet.domain.user.entity.User;

/**
 * 결제 완료 후 아이템 지급의 계약(Contract)을 정의하는 인터페이스.
 * <p>
 * Order 도메인은 이 인터페이스에만 의존하며, 구체적인 지급 도메인(User, Team)을 알지 못합니다.
 * 이를 통해 아이템 지급 로직이 변경되어도 결제 로직에 영향을 주지 않습니다.
 * </p>
 */
public interface ItemGrantService {

    /**
     * 결제 완료 시 상품 유형에 따라 적절한 아이템을 지급합니다.
     *
     * @param productType 구매한 상품 유형
     * @param totalPrice  결제 총 금액
     * @param buyer       구매자 엔티티
     * @return 지급 처리 결과 (구매 내역 생성에 필요한 최소 정보 포함)
     * @throws com.gdg.z_meet.global.exception.BusinessException 지급 대상(팀/프로필)을 찾을 수
     *                                                           없을 때
     */
    ItemGrantResult grant(ProductType productType, Long totalPrice, User buyer);
}
