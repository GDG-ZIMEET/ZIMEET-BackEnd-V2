package com.gdg.z_meet.domain.user.entity;

import com.gdg.z_meet.domain.meeting.entity.enums.Verification;
import com.gdg.z_meet.domain.user.entity.enums.*;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_profile_id", unique = true, nullable = false)
    private Long id;

    @Column(unique = true, nullable = false)
    private String nickname;

    @Column(nullable = false)
    private String emoji;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Music music;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MBTI mbti;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Style style;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdealType idealType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdealAge idealAge;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Grade grade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Major major;

    @Column(nullable = false)
    private int age;

    @Column(nullable = false)
    @Builder.Default
    private int leftDelete = 2;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Level level = Level.LIGHT;

    @Column(nullable = false)
    @Builder.Default
    private int ticket = 2;

    @Column(nullable = false)
    @Builder.Default
    private int hi = 2;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ProfileStatus profileStatus = ProfileStatus.NONE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Verification verification = Verification.NONE;

    @Column(nullable = false)
    @Builder.Default
    private boolean fcmSendOneOne = false;

    @Column(nullable = false)
    @Builder.Default
    private int viewCount = 0;

    @Column(name = "last_notified")
    @Builder.Default
    private Integer lastNotified = 0;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public void increaseTicket(int amount) {
        this.ticket += amount;
    }

    public void decreaseTicket(int amount) {
        this.ticket -= amount;
    }

    public void upgradeToPlus() {
        this.level = Level.PLUS;
    }

    public void addDelete() { this.leftDelete++; }

    public void upLevel() { this.level = Level.PLUS; }

    public void setInfiniteTicket() { this.ticket = 99; }

    public void changeProfileStatus(ProfileStatus status) { this.profileStatus = status; }

    public void setFcmSendOneOne(boolean fcmSendOneOne) { this.fcmSendOneOne = fcmSendOneOne; }

    public int getViewCount() { return viewCount; }

    public void setViewCount(int viewCount) { this.viewCount = viewCount; }

    public void increaseHi(int amount) {
        this.hi += amount;
    }

    public void decreaseHi(int amount) {
        this.hi -= amount;
    }
}
