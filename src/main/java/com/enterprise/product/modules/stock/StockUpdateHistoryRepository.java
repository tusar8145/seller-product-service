package com.enterprise.product.modules.stock;

import com.enterprise.product.modules.stock.model.StockUpdateHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockUpdateHistoryRepository extends JpaRepository<StockUpdateHistory, Long> {
    Page<StockUpdateHistory> findByProductId(Long productId, Pageable pageable);
    boolean existsByReferenceId(String referenceId);
}
