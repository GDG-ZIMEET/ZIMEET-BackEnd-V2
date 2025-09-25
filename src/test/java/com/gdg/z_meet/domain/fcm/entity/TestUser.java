package com.gdg.z_meet.domain.fcm.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "test_users")
public class TestUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private boolean pushAgree;
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public boolean isPushAgree() {
        return pushAgree;
    }
    
    public void setPushAgree(boolean pushAgree) {
        this.pushAgree = pushAgree;
    }
}
