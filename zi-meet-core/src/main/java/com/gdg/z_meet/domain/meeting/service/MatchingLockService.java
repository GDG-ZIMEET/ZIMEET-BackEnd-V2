package com.gdg.z_meet.domain.meeting.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

// 애플리케이션 단위 락 설정
@Service
public class MatchingLockService {
    private final ConcurrentHashMap<Long, ReentrantLock> matchingLocks = new ConcurrentHashMap<>();

    public void lock(Long matchingId) {
        matchingLocks.computeIfAbsent(matchingId, k -> new ReentrantLock()).lock();
    }

    public void unlock(Long matchingId) {
        ReentrantLock lock = matchingLocks.get(matchingId);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
            matchingLocks.remove(matchingId);
        }
    }
}
