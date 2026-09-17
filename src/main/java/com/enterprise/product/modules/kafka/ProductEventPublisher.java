package com.enterprise.product.modules.kafka;

import com.enterprise.product.config.KafkaTopics;
import com.enterprise.product.model.Product;
import com.enterprise.product.modules.kafka.event.ProductEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventPublisher {

    private final KafkaTemplate<String, ProductEvent> productEventKafkaTemplate;

    public void publishProductCreated(Product product) {
        publish(ProductEvent.of("CREATED", product.getId(), product.getName()));
    }
    public void publishProductUpdated(Product product) {
        publish(ProductEvent.of("UPDATED", product.getId(), product.getName()));
    }
    public void publishProductDeleted(Long id) {
        publish(ProductEvent.of("DELETED", id, null));
    }

    private void publish(ProductEvent event) {
        productEventKafkaTemplate.send(KafkaTopics.PRODUCT_EVENTS,
                        String.valueOf(event.getProductId()), event)
                .whenComplete((r, ex) -> {
                    if (ex != null) log.error("Kafka publish failed {}", event.getEventId(), ex);
                    else log.debug("Published {}", event.getEventId());
                });
    }
}
