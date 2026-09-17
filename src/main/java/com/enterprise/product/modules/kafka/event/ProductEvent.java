package com.enterprise.product.modules.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductEvent {
    private String eventId;
    private String eventType;   // CREATED | UPDATED | DELETED
    private Long productId;
    private String productName;
    private OffsetDateTime occurredAt;

    public static ProductEvent of(String type, Long productId, String name) {
        return ProductEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(type)
                .productId(productId)
                .productName(name)
                .occurredAt(OffsetDateTime.now())
                .build();
    }
}
