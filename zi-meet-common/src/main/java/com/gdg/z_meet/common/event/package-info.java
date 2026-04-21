/**
 * 서비스 모듈 간 통신하는 이벤트 스키마.
 *
 * Payment <-> Core 사이 Redis Streams 로 흘리는 메시지 본문의 공통 계약.
 * 각 이벤트는 불변 record 로 정의되며, Jackson 직렬화를 기준으로 한다.
 */
package com.gdg.z_meet.common.event;
