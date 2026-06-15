package com.ecommerce.orders.application.port.out;

import com.ecommerce.orders.domain.model.ProductId;
import com.ecommerce.orders.domain.model.ProductSnapshot;

public interface ProductCatalogGateway {
    ProductSnapshot fetchProduct(ProductId productId);
}
