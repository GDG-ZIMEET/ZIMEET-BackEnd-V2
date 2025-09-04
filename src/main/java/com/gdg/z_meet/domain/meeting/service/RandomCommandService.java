package com.gdg.z_meet.domain.meeting.service;

public interface RandomCommandService {

    void joinMatching(Long userId);
    void cancelMatching(Long userId);
}
