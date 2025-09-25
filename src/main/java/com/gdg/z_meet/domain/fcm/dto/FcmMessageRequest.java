package com.gdg.z_meet.domain.fcm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FcmMessageRequest implements Serializable {
    
    private String messageId;
    private FcmType type;
    private String title;
    private String body;
    private List<String> fcmTokens;
    private Long userId;
    private Map<String, String> data;
    private LocalDateTime createdAt;
    
    public enum FcmType {
        SINGLE,     // 단일 사용자
        BROADCAST,  // 전체 브로드캐스트
        TEST        // 테스트용
    }
}
