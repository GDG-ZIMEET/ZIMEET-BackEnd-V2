package com.gdg.z_meet.domain.meeting.entity;

import com.gdg.z_meet.domain.meeting.entity.enums.ActiveStatus;
import com.gdg.z_meet.domain.meeting.entity.enums.Event;
import com.gdg.z_meet.domain.meeting.entity.enums.TeamType;
import com.gdg.z_meet.domain.meeting.entity.enums.Verification;
import com.gdg.z_meet.global.common.BaseEntity;
import com.gdg.z_meet.domain.user.entity.enums.Gender;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Team extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "team_id", unique = true)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TeamType teamType;

    @Column(nullable = false)
    @Size(min = 1, max = 7)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(nullable = false)
    @Builder.Default
    private Integer hi = 2;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Verification verification = Verification.NONE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ActiveStatus activeStatus = ActiveStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Event event;

    @Column(nullable = false)
    @Builder.Default
    private int viewCount = 0;

    @Column(name = "last_notified")
    @Builder.Default
    private Integer lastNotified = 0;

    // hi 값을 변경하는 메서드
    public void decreaseHi() {
        if (this.hi > 0) {
            this.hi--;
        } else {
            throw new BusinessException(Code.HI_COUNT_ZERO);
        }
    }

    public void increaseHi(int amount) {
        this.hi += amount;
    }

    public void inactivateTeam() {
        this.activeStatus = ActiveStatus.INACTIVE;
    }

    public void setInfiniteHi() { this.hi = 99; }

    public void patchVerification() { this.verification = Verification.COMPLETE; }

    public int getViewCount() { return viewCount; }

    public void setViewCount(int viewCount) { this.viewCount = viewCount; }

    public void setLastNotified(Integer lastNotified) { this.lastNotified = lastNotified; }
}
