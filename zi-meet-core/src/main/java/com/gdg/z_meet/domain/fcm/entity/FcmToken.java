package com.gdg.z_meet.domain.fcm.entity;

import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Builder
@Entity
@Table(name = "fcm_token"
// uniqueConstraints = {
// @UniqueConstraint(name = "uk_fcm_token_user", columnNames = "user_id"),
// @UniqueConstraint(name = "uk_fcm_token_value", columnNames = "token")
// }
)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FcmToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, referencedColumnName = "user_id")
    private User user;

    @Column(nullable = false)
    private String token;

    public FcmToken(final String token, final User user) {
        this.token = token;
        this.user = user;
    }

    public void updateToken(String newToken) {
        this.token = newToken;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setUser(User user) {
        this.user = user;
        user.setFcmToken(this);
    }
}