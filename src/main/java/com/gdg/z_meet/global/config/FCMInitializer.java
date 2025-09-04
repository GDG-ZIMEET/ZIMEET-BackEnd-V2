package com.gdg.z_meet.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FCMInitializer {

    @Value("${firebase.admin-sdk}")
    private String serviceAccountData;

    @PostConstruct
    public void initialize() {
        System.out.println("🔥 FCMInitializer: initialize() 시작됨");
        
        if (serviceAccountData == null || serviceAccountData.trim().isEmpty()) {
            System.out.println("⚠️ Firebase service account 데이터가 없어 초기화를 건너뜀");
            return;
        }
        
        try {
            // 환경 변수에서 가져온 JSON 문자열을 InputStream으로 변환
            InputStream serviceAccount = new ByteArrayInputStream(serviceAccountData.getBytes());

            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            // 중복 초기화 방지
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                System.out.println("✅ Firebase 초기화 완료 (환경 변수 사용)");
            } else {
                System.out.println("ℹ️ 이미 Firebase 초기화됨");
            }
        } catch (IOException e) {
            System.err.println("❌ Firebase 초기화 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
