package com.enterprise.product.modules.bulk;

import com.enterprise.product.modules.bulk.model.BulkJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BulkJobRepository extends JpaRepository<BulkJob, UUID> {
}
