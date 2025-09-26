package com.gdg.z_meet.domain.fcm.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "test_fcm_tokens")
public class TestFcmToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "user_id")
    private TestUser user;
    
    private String token;
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public TestUser getUser() {
        return user;
    }
    
    public void setUser(TestUser user) {
        this.user = user;
    }
    
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
}
