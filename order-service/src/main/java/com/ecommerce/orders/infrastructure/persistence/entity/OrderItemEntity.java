package com.ecommerce.orders.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItemEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_name")
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_amount", precision = 19, scale = 2)
    private BigDecimal unitPriceAmount;

    @Column(name = "unit_price_currency", length = 3)
    private String unitPriceCurrency;

    protected OrderItemEntity() {}

    public OrderItemEntity(UUID id, OrderEntity order, UUID productId, String productName,
                           int quantity, BigDecimal unitPriceAmount, String unitPriceCurrency) {
        this.id = id;
        this.order = order;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPriceAmount = unitPriceAmount;
        this.unitPriceCurrency = unitPriceCurrency;
    }

    public UUID getId() { return id; }
    public OrderEntity getOrder() { return order; }
    public UUID getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPriceAmount() { return unitPriceAmount; }
    public String getUnitPriceCurrency() { return unitPriceCurrency; }

    public void setProductName(String productName) { this.productName = productName; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setUnitPriceAmount(BigDecimal unitPriceAmount) { this.unitPriceAmount = unitPriceAmount; }
    public void setUnitPriceCurrency(String unitPriceCurrency) { this.unitPriceCurrency = unitPriceCurrency; }
}
