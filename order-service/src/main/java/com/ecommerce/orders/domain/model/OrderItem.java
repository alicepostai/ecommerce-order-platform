package com.ecommerce.orders.domain.model;

public class OrderItem {

    private final OrderItemId id;
    private final ProductId productId;
    private String productName;
    private Quantity quantity;
    private Money unitPrice;

    public OrderItem(OrderItemId id, ProductId productId, Quantity quantity) {
        this.id = id;
        this.productId = productId;
        this.quantity = quantity;
    }

    /** Reconstitui um item já persistido (productName e unitPrice podem ser nulos antes da confirmação). */
    public static OrderItem reconstitute(OrderItemId id, ProductId productId,
                                         String productName, Quantity quantity, Money unitPrice) {
        var item = new OrderItem(id, productId, quantity);
        item.productName = productName;
        item.unitPrice = unitPrice;
        return item;
    }

    void incrementQuantity(Quantity additional) {
        this.quantity = this.quantity.add(additional);
    }

    void applySnapshot(ProductSnapshot snapshot) {
        this.productName = snapshot.productName();
        this.unitPrice = snapshot.unitPrice();
    }

    Money lineTotal() {
        if (unitPrice == null) throw new IllegalStateException("Item price not yet set — confirm the order first");
        return unitPrice.multiply(quantity.value());
    }

    public OrderItemId getId() { return id; }
    public ProductId getProductId() { return productId; }
    public String getProductName() { return productName; }
    public Quantity getQuantity() { return quantity; }
    public Money getUnitPrice() { return unitPrice; }
}
