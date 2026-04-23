package com.gdg.z_meet.core.stream;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

import java.time.Duration;

/**
 * payment.events 스트림 전용 컨테이너.
 *
 * 기존 RedisStreamConfig 의 컨테이너는 ObjectRecord (FCM) 용으로 만들어져 있고
 * 타입 파라미터가 호환되지 않는다. 결제 이벤트는 평이한 Map(String, String) 페이로드라
 * MapRecord 로 받기 위해 별도 컨테이너를 둔다.
 *
 * core-worker 프로파일에서만 빈 등록.
 */
@Configuration
@Profile("core-worker")
public class PaymentStreamConfig {

    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> paymentEventsContainer(
            RedisConnectionFactory connectionFactory) {
        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(1))
                        .build();

        return StreamMessageListenerContainer.create(connectionFactory, options);
    }
}
