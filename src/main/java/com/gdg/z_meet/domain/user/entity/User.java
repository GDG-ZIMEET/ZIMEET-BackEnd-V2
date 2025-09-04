package com.gdg.z_meet.domain.user.entity;

import com.gdg.z_meet.domain.fcm.entity.FcmToken;
import com.gdg.z_meet.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="user_id", unique = true, nullable = false)
    private Long id;

    @Column(unique = true, nullable = false)
    private String studentNumber;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String phoneNumber;

    @Column(name = "is_deleted", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean isDeleted;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private UserProfile userProfile;

    @ColumnDefault("false")
    private boolean pushAgree;

    @Column(nullable = false)
    @Builder.Default
    private boolean fcmSendTwoTwo = false;

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    private FcmToken fcmToken;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return null;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return studentNumber;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public void setIsDeleted(boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setPushAgree(boolean pushAgree) { this.pushAgree = pushAgree;}

    public void setFcmSendTwoTwo(boolean fcmSendTwoTwo) {this.fcmSendTwoTwo = fcmSendTwoTwo;}

    public void setFcmToken(FcmToken fcmToken) {
        this.fcmToken = fcmToken;
        if (fcmToken.getUser() != this) {
            fcmToken.setUser(this);
        }
    }

}
