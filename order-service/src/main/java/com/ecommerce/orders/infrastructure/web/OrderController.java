package com.ecommerce.orders.infrastructure.web;

import com.ecommerce.orders.application.dto.*;
import com.ecommerce.orders.application.port.in.*;
import com.ecommerce.orders.infrastructure.web.dto.AddOrderItemRequest;
import com.ecommerce.orders.infrastructure.web.dto.CreateOrderRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CreateOrderUseCase createOrder;
    private final GetOrderUseCase getOrder;
    private final ListCustomerOrdersUseCase listOrders;
    private final AddOrderItemUseCase addItem;
    private final RemoveOrderItemUseCase removeItem;
    private final ConfirmOrderUseCase confirmOrder;
    private final CancelOrderUseCase cancelOrder;

    public OrderController(CreateOrderUseCase createOrder,
                           GetOrderUseCase getOrder,
                           ListCustomerOrdersUseCase listOrders,
                           AddOrderItemUseCase addItem,
                           RemoveOrderItemUseCase removeItem,
                           ConfirmOrderUseCase confirmOrder,
                           CancelOrderUseCase cancelOrder) {
        this.createOrder = createOrder;
        this.getOrder = getOrder;
        this.listOrders = listOrders;
        this.addItem = addItem;
        this.removeItem = removeItem;
        this.confirmOrder = confirmOrder;
        this.cancelOrder = cancelOrder;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_orders:write')")
    public ResponseEntity<OrderResult> createOrder(@Valid @RequestBody CreateOrderRequest req) {
        var result = createOrder.create(new CreateOrderCommand(req.customerId()));
        return ResponseEntity
                .created(URI.create("/api/v1/orders/" + result.id()))
                .body(result);
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAuthority('SCOPE_orders:read')")
    public OrderResult getOrder(@PathVariable UUID orderId) {
        return getOrder.getById(orderId);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_orders:read')")
    public List<OrderResult> listOrders(@RequestParam UUID customerId) {
        return listOrders.listByCustomer(customerId);
    }

    @PostMapping("/{orderId}/items")
    @PreAuthorize("hasAuthority('SCOPE_orders:write')")
    public OrderResult addItem(@PathVariable UUID orderId, @Valid @RequestBody AddOrderItemRequest req) {
        return addItem.addItem(new AddOrderItemCommand(orderId, req.productId(), req.quantity()));
    }

    @DeleteMapping("/{orderId}/items/{itemId}")
    @PreAuthorize("hasAuthority('SCOPE_orders:write')")
    public OrderResult removeItem(@PathVariable UUID orderId, @PathVariable UUID itemId) {
        return removeItem.removeItem(new RemoveOrderItemCommand(orderId, itemId));
    }

    @PostMapping("/{orderId}/confirm")
    @PreAuthorize("hasAuthority('SCOPE_orders:write')")
    public OrderResult confirmOrder(@PathVariable UUID orderId) {
        return confirmOrder.confirm(new ConfirmOrderCommand(orderId));
    }

    @DeleteMapping("/{orderId}")
    @PreAuthorize("hasAuthority('SCOPE_orders:write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelOrder(@PathVariable UUID orderId) {
        cancelOrder.cancel(new CancelOrderCommand(orderId));
    }
}
