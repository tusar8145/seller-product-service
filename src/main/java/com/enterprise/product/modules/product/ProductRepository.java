package com.enterprise.product.modules.product;

import com.enterprise.product.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
          SELECT p FROM Product p
          WHERE (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))
          """)
    Page<Product> search(@Param("keyword") String keyword, Pageable pageable);

    /**
     * Idempotent, version-checked stock update used by the async DB worker.
     * The version check prevents lost updates under concurrent writers.
     */
    @Modifying
    @Query("""
           UPDATE Product p
              SET p.stock = :newStock, p.version = p.version + 1
            WHERE p.id = :id AND p.version = :expectedVersion
           """)
    int updateStockIfVersionMatches(@Param("id") Long id,
                                    @Param("newStock") long newStock,
                                    @Param("expectedVersion") long expectedVersion);
}
