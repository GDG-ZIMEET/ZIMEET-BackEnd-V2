package com.gdg.z_meet.domain.meeting.entity;

import com.gdg.z_meet.domain.meeting.entity.enums.HiStatus;
import com.gdg.z_meet.domain.meeting.entity.enums.HiType;
import com.gdg.z_meet.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Hi extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hi_id", unique = true)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HiStatus hiStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HiType hiType;

    @Column(name = "from_id")
    private Long fromId;

    @Column(name = "to_id")
    private Long toId;

    @Column(nullable = false)
    @Builder.Default
    boolean fcmSendHiToUser = false;

    @Column(nullable = false)
    @Builder.Default
    boolean fcmSendHiToTeam = false;

    // hi Status 변경
    public void setChangeStatus(HiStatus hiStatus) {
       this.hiStatus=hiStatus;
    }

}
