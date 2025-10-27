package com.gdg.z_meet.domain.order.repository;

public interface NamedLockRepository {

    /**
     * 네임드 락 할당
     * @param lockName 락 이름
     * @return 락 획득 결과 (1: 성공, 0: 실패, null: 에러)
     */
    Integer getLock(String lockName);

    /**
     * 네임드 락 해제
     * @param lockName 락 이름
     * @return 락 해제 결과
     */
    Integer releaseLock(String lockName);
}