package com.example.DormlyBackend.configuration.notification;

import com.example.DormlyBackend.enums.ChannelType;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    @Bean
    public NewTopic emailTopic() {
        return TopicBuilder.name(ChannelType.EMAIL.topic())
                .partitions(3).replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "604800000") // 7 ngày
                .build();
    }

    @Bean public NewTopic smsTopic() {
        return TopicBuilder.name(ChannelType.SMS.topic()).partitions(3).replicas(1).build();
    }

    @Bean public NewTopic pushTopic() {
        return TopicBuilder.name(ChannelType.PUSH.topic()).partitions(3).replicas(1).build();
    }

    @Bean public NewTopic webSocketTopic() {
        return TopicBuilder.name(ChannelType.WEBSOCKET.topic()).partitions(1).replicas(1).build();
    }
}