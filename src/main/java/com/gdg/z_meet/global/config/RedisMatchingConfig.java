package com.gdg.z_meet.global.config;

import com.gdg.z_meet.domain.meeting.MatchingMessageSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
public class RedisMatchingConfig {

    @Bean
    public PatternTopic matchingTopic() {

        return new PatternTopic("matching.*");
    }

    @Bean
    public MessageListenerAdapter matchingMessageListener(MatchingMessageSubscriber subscriber) {

        return new MessageListenerAdapter(subscriber, "handleMessage");
    }

    @Bean
    public RedisMessageListenerContainer matchingMessageContainer(RedisConnectionFactory connectionFactory,
                                                                  MessageListenerAdapter matchingMessageListener,
                                                                  PatternTopic matchingTopic) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(matchingMessageListener, matchingTopic);
        return container;
    }
}