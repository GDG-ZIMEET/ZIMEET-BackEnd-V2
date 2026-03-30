package com.gdg.z_meet.global.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.AbstractProtocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * RST 패킷 강제 유발 테스트용 Tomcat 설정.
 *
 * SO_LINGER=0 을 Tomcat 소켓에 주입하면,
 * 커넥션을 닫을 때 정상적인 FIN 4-Way Handshake 대신
 * TCP RST 패킷을 즉시 발사하도록 OS에 지시합니다.
 *
 * @Profile("rst-test") : 이 설정은 반드시 이 프로파일에서만 활성화됩니다.
 */
@Slf4j
@Configuration
@Profile("rst-test")
public class RstTestTomcatConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> rstLingerCustomizer() {
        return factory -> factory.addConnectorCustomizers(connector -> {
            if (connector.getProtocolHandler() instanceof AbstractProtocol<?> protocol) {
                // connectionLinger=0 → 소켓 닫을 때 즉시 RST 발사 (FIN 없음)
                protocol.setConnectionLinger(0);
                log.warn("[RST-TEST] SO_LINGER=0 적용됨 - 모든 커넥션 종료 시 RST 발사");
            }
        });
    }
}
