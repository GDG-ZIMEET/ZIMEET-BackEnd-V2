package com.gdg.z_meet.domain.event;

import com.gdg.z_meet.domain.meeting.entity.Team;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;

public enum Product {
    HI_300(1, 0, 0), HI_800(3, 0, 0), HI_2500(10, 0, 0),
    HI_500(0, 1, 0), HI_1000(0, 3, 0), HI_3000(0, 0, 10),
    TICKET_500(0, 0, 1),TICKET_1200(0, 0, 3),TICKET_3000(0, 0, 8);

    private final int myHi;
    private final int teamHi;
    private final int ticket;

    Product(int myHi, int teamHi, int ticket) {
        this.myHi = myHi;
        this.teamHi = teamHi;
        this.ticket = ticket;
    }

    public boolean needsTeam() {
        return teamHi > 0;
    }

    public void payProduct(User user, Team team) {
        if (myHi > 0) {
            user.getUserProfile().increaseHi(myHi);
        }
        if (teamHi > 0) {
            team.increaseHi(teamHi);
        }
        if (ticket > 0) {
            user.getUserProfile().increaseTicket(ticket);
        }
    }
}
