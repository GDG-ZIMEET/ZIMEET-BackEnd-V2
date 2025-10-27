package com.gdg.z_meet.domain.order.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

@Repository
public class NamedLockRepositoryImpl implements NamedLockRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Integer getLock(String lockName) {
        Query query = entityManager.createNativeQuery("SELECT GET_LOCK(?, 10)");
        query.setParameter(1, lockName);
        return (Integer) query.getSingleResult();
    }

    @Override
    public Integer releaseLock(String lockName) {
        Query query = entityManager.createNativeQuery("SELECT RELEASE_LOCK(?)");
        query.setParameter(1, lockName);
        return (Integer) query.getSingleResult();
    }
}