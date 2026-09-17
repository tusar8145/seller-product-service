package com.enterprise.product.config.kafka;

import com.enterprise.product.config.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${kafka.topics.partitions:10}")
    private int partitions;

    @Bean
    public NewTopic productEventsTopic() {
        return TopicBuilder.name(KafkaTopics.PRODUCT_EVENTS)
                .partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic bulkImportTopic() {
        return TopicBuilder.name(KafkaTopics.PRODUCT_BULK_IMPORT)
                .partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic bulkImportDlqTopic() {
        return TopicBuilder.name(KafkaTopics.PRODUCT_BULK_IMPORT_DLQ)
                .partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic stockUpdateTopic() {
        return TopicBuilder.name(KafkaTopics.PRODUCT_STOCK_UPDATE)
                .partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic stockUpdateDlqTopic() {
        return TopicBuilder.name(KafkaTopics.PRODUCT_STOCK_UPDATE_DLQ)
                .partitions(partitions).replicas(1).build();
    }
}
