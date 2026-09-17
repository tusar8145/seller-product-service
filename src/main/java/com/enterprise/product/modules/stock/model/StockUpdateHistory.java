package com.enterprise.product.modules.stock.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "stock_update_history", indexes = {
        @Index(name = "idx_stock_history_product", columnList = "product_id"),
        @Index(name = "idx_stock_history_ref", columnList = "reference_id"),
        @Index(name = "idx_stock_history_created", columnList = "created_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockUpdateHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "update_type", nullable = false, length = 20)
    private StockUpdateType updateType;

    @Column(name = "delta", nullable = false)
    private Long delta;

    @Column(name = "stock_before", nullable = false)
    private Long stockBefore;

    @Column(name = "stock_after", nullable = false)
    private Long stockAfter;

    /** Idempotency / request reference — must be unique per change event. */
    @Column(name = "reference_id", nullable = false, length = 100)
    private String referenceId;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
