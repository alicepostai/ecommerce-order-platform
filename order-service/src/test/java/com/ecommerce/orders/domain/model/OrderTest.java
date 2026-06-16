package com.ecommerce.orders.domain.model;

import com.ecommerce.orders.domain.event.*;
import com.ecommerce.orders.domain.exception.InvalidStateTransitionException;
import com.ecommerce.orders.domain.exception.OrderItemNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class OrderTest {

    // ── Fixtures ─────────────────────────────────────────────────────────────

    static final CustomerId CUSTOMER  = CustomerId.of("11111111-1111-1111-1111-111111111111");
    static final ProductId  PROD_A    = ProductId.of("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final ProductId  PROD_B    = ProductId.of("cccccccc-cccc-cccc-cccc-cccccccccccc");
    static final Money      PRICE_A   = Money.of("199.90", "BRL");
    static final Money      PRICE_B   = Money.of("49.90", "BRL");

    static final Map<ProductId, ProductSnapshot> SNAP_A = Map.of(
            PROD_A, new ProductSnapshot(PROD_A, "Teclado Mecânico TKL", PRICE_A));

    static final Map<ProductId, ProductSnapshot> SNAP_AB = Map.of(
            PROD_A, new ProductSnapshot(PROD_A, "Teclado Mecânico TKL", PRICE_A),
            PROD_B, new ProductSnapshot(PROD_B, "Mouse Sem Fio", PRICE_B));

    Order newOrder() {
        return Order.create(OrderId.generate(), CUSTOMER);
    }

    Order orderWithItem(ProductId productId, int qty) {
        var o = newOrder();
        o.addItem(OrderItemId.generate(), productId, Quantity.of(qty));
        o.pullDomainEvents();
        return o;
    }

    Order confirmedOrder() {
        var o = orderWithItem(PROD_A, 1);
        o.confirm(SNAP_A);
        o.pullDomainEvents();
        return o;
    }

    Order paymentPendingOrder() {
        var o = confirmedOrder();
        o.initiatePayment();
        o.pullDomainEvents();
        return o;
    }

    Order orderWithRejections(int count) {
        var o = confirmedOrder();
        for (int i = 0; i < count; i++) {
            o.initiatePayment();
            o.applyPaymentRejected();
        }
        o.pullDomainEvents();
        return o;
    }

    // ── CreateOrder ───────────────────────────────────────────────────────────

    @Test
    void create_starts_in_created_status() {
        assertThat(newOrder().getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void create_emits_order_created_event() {
        var order = newOrder();
        var events = order.pullDomainEvents();
        assertThat(events).hasSize(1)
                .first().isInstanceOf(OrderCreated.class);
        assertThat(((OrderCreated) events.get(0)).customerId()).isEqualTo(CUSTOMER);
    }

    @Test
    void create_rejects_null_id() {
        assertThatThrownBy(() -> Order.create(null, CUSTOMER))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void create_rejects_null_customer() {
        assertThatThrownBy(() -> Order.create(OrderId.generate(), null))
                .isInstanceOf(NullPointerException.class);
    }

    // ── AddItem em CREATED ────────────────────────────────────────────────────

    @Test
    void add_item_adds_new_product() {
        var order = newOrder();
        order.addItem(OrderItemId.generate(), PROD_A, Quantity.of(2));
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getQuantity().value()).isEqualTo(2);
    }

    @Test
    void add_item_increments_quantity_for_duplicate_product() {
        var order = newOrder();
        order.addItem(OrderItemId.generate(), PROD_A, Quantity.of(2));
        order.addItem(OrderItemId.generate(), PROD_A, Quantity.of(3));
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getQuantity().value()).isEqualTo(5);
    }

    @Test
    void add_item_emits_event() {
        var order = newOrder();
        order.addItem(OrderItemId.generate(), PROD_A, Quantity.of(1));
        var events = order.pullDomainEvents();
        assertThat(events).anyMatch(e -> e instanceof OrderItemAdded);
    }

    @Test
    void add_item_rejected_when_confirmed() {
        var order = confirmedOrder();
        assertThatThrownBy(() -> order.addItem(OrderItemId.generate(), PROD_A, Quantity.of(1)))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasFieldOrPropertyWithValue("errorCode", "order-not-modifiable");
    }

    @Test
    void add_item_rejected_when_payment_pending() {
        assertThatThrownBy(() -> paymentPendingOrder().addItem(OrderItemId.generate(), PROD_A, Quantity.of(1)))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void add_item_rejected_when_paid() {
        var order = paymentPendingOrder();
        order.applyPaymentApproved();
        assertThatThrownBy(() -> order.addItem(OrderItemId.generate(), PROD_A, Quantity.of(1)))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void add_item_rejected_when_cancelled() {
        var order = newOrder();
        order.cancel(CancellationReason.CUSTOMER_REQUEST);
        assertThatThrownBy(() -> order.addItem(OrderItemId.generate(), PROD_A, Quantity.of(1)))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    // ── RemoveItem em CREATED ─────────────────────────────────────────────────

    @Test
    void remove_item_removes_existing_item() {
        var order = newOrder();
        var itemId = OrderItemId.generate();
        order.addItem(itemId, PROD_A, Quantity.of(1));
        order.removeItem(itemId);
        assertThat(order.getItems()).isEmpty();
    }

    @Test
    void remove_item_emits_event() {
        var order = newOrder();
        var itemId = OrderItemId.generate();
        order.addItem(itemId, PROD_A, Quantity.of(1));
        order.pullDomainEvents();
        order.removeItem(itemId);
        assertThat(order.pullDomainEvents()).anyMatch(e -> e instanceof OrderItemRemoved);
    }

    @Test
    void remove_item_throws_when_item_not_found() {
        var order = newOrder();
        assertThatThrownBy(() -> order.removeItem(OrderItemId.generate()))
                .isInstanceOf(OrderItemNotFoundException.class);
    }

    @Test
    void remove_item_rejected_when_confirmed() {
        var order = confirmedOrder();
        assertThatThrownBy(() -> order.removeItem(OrderItemId.generate()))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasFieldOrPropertyWithValue("errorCode", "order-not-modifiable");
    }

    // ── Confirm ───────────────────────────────────────────────────────────────

    @Test
    void confirm_transitions_to_confirmed() {
        var order = orderWithItem(PROD_A, 1);
        order.confirm(SNAP_A);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void confirm_calculates_total_from_snapshots() {
        // 2 × 199.90 + 1 × 49.90 = 449.70 (cenário Gherkin)
        var order = newOrder();
        order.addItem(OrderItemId.generate(), PROD_A, Quantity.of(2));
        order.addItem(OrderItemId.generate(), PROD_B, Quantity.of(1));
        order.confirm(SNAP_AB);
        assertThat(order.getTotal().amount()).isEqualByComparingTo("449.70");
        assertThat(order.getTotal().currency()).isEqualTo("BRL");
    }

    @Test
    void confirm_applies_price_snapshot_to_items() {
        var order = orderWithItem(PROD_A, 2);
        order.confirm(SNAP_A);
        var item = order.getItems().get(0);
        assertThat(item.getProductName()).isEqualTo("Teclado Mecânico TKL");
        assertThat(item.getUnitPrice()).isEqualTo(PRICE_A);
    }

    @Test
    void confirm_emits_order_confirmed_event() {
        var order = orderWithItem(PROD_A, 1);
        order.confirm(SNAP_A);
        assertThat(order.pullDomainEvents()).anyMatch(e -> e instanceof OrderConfirmed);
    }

    @Test
    void confirm_is_idempotent_when_already_confirmed() {
        var order = confirmedOrder();
        var totalBefore = order.getTotal();
        order.confirm(SNAP_A); // segunda chamada — no-op
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getTotal()).isEqualTo(totalBefore);
        assertThat(order.pullDomainEvents()).isEmpty(); // nenhum evento extra
    }

    @Test
    void confirm_rejects_empty_order() {
        var order = newOrder();
        assertThatThrownBy(() -> order.confirm(Map.of()))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasFieldOrPropertyWithValue("errorCode", "empty-order");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void confirm_rejected_when_payment_pending() {
        assertThatThrownBy(() -> paymentPendingOrder().confirm(SNAP_A))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasFieldOrPropertyWithValue("errorCode", "order-not-confirmable");
    }

    @Test
    void confirm_rejected_when_paid() {
        var order = paymentPendingOrder();
        order.applyPaymentApproved();
        assertThatThrownBy(() -> order.confirm(SNAP_A))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void confirm_rejected_when_cancelled() {
        var order = newOrder();
        order.cancel(CancellationReason.CUSTOMER_REQUEST);
        assertThatThrownBy(() -> order.confirm(Map.of()))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    @Test
    void cancel_from_created_sets_customer_request_reason() {
        var order = newOrder();
        order.cancel(CancellationReason.CUSTOMER_REQUEST);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo(CancellationReason.CUSTOMER_REQUEST);
    }

    @Test
    void cancel_from_confirmed_transitions_to_cancelled() {
        var order = confirmedOrder();
        order.cancel(CancellationReason.CUSTOMER_REQUEST);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancel_from_payment_pending_transitions_to_cancelled() {
        var order = paymentPendingOrder();
        order.cancel(CancellationReason.CUSTOMER_REQUEST);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancel_emits_order_cancelled_event() {
        var order = confirmedOrder();
        order.cancel(CancellationReason.CUSTOMER_REQUEST);
        assertThat(order.pullDomainEvents())
                .anyMatch(e -> e instanceof OrderCancelled oc
                        && oc.reason() == CancellationReason.CUSTOMER_REQUEST);
    }

    @Test
    void cancel_from_paid_throws_order_not_cancellable() {
        var order = paymentPendingOrder();
        order.applyPaymentApproved();
        assertThatThrownBy(() -> order.cancel(CancellationReason.CUSTOMER_REQUEST))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasFieldOrPropertyWithValue("errorCode", "order-not-cancellable");
    }

    @Test
    void cancel_from_cancelled_throws() {
        var order = newOrder();
        order.cancel(CancellationReason.CUSTOMER_REQUEST);
        assertThatThrownBy(() -> order.cancel(CancellationReason.CUSTOMER_REQUEST))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasFieldOrPropertyWithValue("errorCode", "order-not-cancellable");
    }

    // ── InitiatePayment ───────────────────────────────────────────────────────

    @Test
    void initiate_payment_transitions_to_payment_pending() {
        var order = confirmedOrder();
        order.initiatePayment();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    }

    @Test
    void initiate_payment_emits_payment_initiated_event() {
        var order = confirmedOrder();
        order.initiatePayment();
        assertThat(order.pullDomainEvents()).anyMatch(e -> e instanceof PaymentInitiated);
    }

    @Test
    void initiate_payment_rejects_when_already_pending() {
        var order = paymentPendingOrder();
        assertThatThrownBy(order::initiatePayment)
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasFieldOrPropertyWithValue("errorCode", "payment-already-pending");
    }

    @Test
    void initiate_payment_rejects_when_created() {
        assertThatThrownBy(() -> newOrder().initiatePayment())
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasFieldOrPropertyWithValue("errorCode", "order-not-confirmed");
    }

    @Test
    void initiate_payment_rejects_when_paid() {
        var order = paymentPendingOrder();
        order.applyPaymentApproved();
        assertThatThrownBy(order::initiatePayment)
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void initiate_payment_rejects_when_cancelled() {
        var order = newOrder();
        order.cancel(CancellationReason.CUSTOMER_REQUEST);
        assertThatThrownBy(order::initiatePayment)
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    // ── PaymentApproved ───────────────────────────────────────────────────────

    @Test
    void payment_approved_transitions_to_paid() {
        var order = paymentPendingOrder();
        order.applyPaymentApproved();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void payment_approved_emits_order_paid_event() {
        var order = paymentPendingOrder();
        order.applyPaymentApproved();
        assertThat(order.pullDomainEvents()).anyMatch(e -> e instanceof OrderPaid);
    }

    @Test
    void payment_approved_is_idempotent_when_already_paid() {
        var order = paymentPendingOrder();
        order.applyPaymentApproved();
        order.pullDomainEvents();
        order.applyPaymentApproved(); // replay — no-op
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.pullDomainEvents()).isEmpty();
    }

    // ── PaymentRejected ───────────────────────────────────────────────────────

    @Test
    void first_rejection_returns_to_confirmed_with_attempt_count_1() {
        var order = paymentPendingOrder();
        order.applyPaymentRejected();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPaymentAttempts()).isEqualTo(1);
    }

    @Test
    void second_rejection_returns_to_confirmed_with_attempt_count_2() {
        var order = orderWithRejections(1);
        order.initiatePayment();
        order.applyPaymentRejected();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPaymentAttempts()).isEqualTo(2);
    }

    @Test
    void third_rejection_auto_cancels_with_payment_attempts_exceeded() {
        var order = orderWithRejections(2);
        order.initiatePayment();
        order.applyPaymentRejected();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentAttempts()).isEqualTo(3);
        assertThat(order.getCancellationReason()).isEqualTo(CancellationReason.PAYMENT_ATTEMPTS_EXCEEDED);
    }

    @Test
    void third_rejection_emits_order_cancelled_event_with_correct_reason() {
        var order = orderWithRejections(2);
        order.initiatePayment();
        order.applyPaymentRejected();
        assertThat(order.pullDomainEvents())
                .anyMatch(e -> e instanceof OrderCancelled oc
                        && oc.reason() == CancellationReason.PAYMENT_ATTEMPTS_EXCEEDED);
    }

    @Test
    void payment_rejected_throws_when_not_payment_pending() {
        assertThatThrownBy(() -> confirmedOrder().applyPaymentRejected())
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    // ── PAID / CANCELLED — tudo proibido ─────────────────────────────────────

    @Test
    void paid_order_rejects_all_mutations() {
        var order = paymentPendingOrder();
        order.applyPaymentApproved();
        assertThatThrownBy(() -> order.cancel(CancellationReason.CUSTOMER_REQUEST))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> order.addItem(OrderItemId.generate(), PROD_A, Quantity.of(1)))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> order.applyPaymentRejected())
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void cancelled_order_rejects_all_commands() {
        var order = newOrder();
        order.cancel(CancellationReason.CUSTOMER_REQUEST);
        assertThatThrownBy(() -> order.addItem(OrderItemId.generate(), PROD_A, Quantity.of(1)))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> order.cancel(CancellationReason.CUSTOMER_REQUEST))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> order.initiatePayment())
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void apply_payment_approved_throws_when_not_payment_pending() {
        var order = confirmedOrder();
        assertThatThrownBy(order::applyPaymentApproved)
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void remove_second_item_leaves_first_item_intact() {
        var order = newOrder();
        var item1Id = OrderItemId.generate();
        var item2Id = OrderItemId.generate();
        order.addItem(item1Id, PROD_A, Quantity.of(1));
        order.addItem(item2Id, PROD_B, Quantity.of(2));
        order.removeItem(item2Id);
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getId()).isEqualTo(item1Id);
    }

    // ── pullDomainEvents limpa a lista ────────────────────────────────────────

    @Test
    void pull_domain_events_clears_the_list() {
        var order = newOrder();
        assertThat(order.pullDomainEvents()).hasSize(1);
        assertThat(order.pullDomainEvents()).isEmpty();
    }
}
