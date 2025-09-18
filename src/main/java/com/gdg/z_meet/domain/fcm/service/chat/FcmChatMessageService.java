package com.gdg.z_meet.domain.fcm.service.chat;

import com.gdg.z_meet.domain.chat.dto.ChatMessage;
import com.gdg.z_meet.domain.fcm.service.core.FcmDomainMessageService;
import com.gdg.z_meet.domain.user.entity.User;

public interface FcmChatMessageService extends FcmDomainMessageService {
    
    void messagingChat(ChatMessage chatMessage);
    
    void messagingOpenChatRoom(User user, Long roomId);
}
