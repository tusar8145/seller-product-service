package com.enterprise.product.modules.product;

import com.enterprise.product.modules.product.dto.ProductRequest;
import com.enterprise.product.modules.product.dto.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {
    ProductResponse create(ProductRequest request);
    ProductResponse getById(Long id);
    ProductResponse getByIdFromMaster(Long id);
    Page<ProductResponse> list(String keyword, Pageable pageable);
    ProductResponse update(Long id, ProductRequest request);
    void delete(Long id);
}
