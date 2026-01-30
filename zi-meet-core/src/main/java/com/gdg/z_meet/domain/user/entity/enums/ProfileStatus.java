package com.gdg.z_meet.domain.user.entity.enums;

public enum ProfileStatus {
    NONE, ACTIVE, INACTIVE;

    public static boolean isUpdatable(ProfileStatus status) {
        return status == ACTIVE || status == INACTIVE;
    }
}
