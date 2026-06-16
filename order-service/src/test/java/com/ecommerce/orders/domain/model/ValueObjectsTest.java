package com.ecommerce.orders.domain.model;

import com.ecommerce.orders.domain.event.LatePaymentResultReceived;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ValueObjectsTest {

    // ─── IDs tipados ───────────────────────────────────────────────────────────

    @Test
    void order_id_generate_is_unique() {
        assertThat(OrderId.generate()).isNotEqualTo(OrderId.generate());
    }

    @Test
    void order_id_of_parses_valid_uuid_string() {
        var uuid = UUID.randomUUID().toString();
        assertThat(OrderId.of(uuid).value().toString()).isEqualTo(uuid);
    }

    @Test
    void typed_ids_of_factory_returns_non_null() {
        var uuid = UUID.randomUUID().toString();
        assertThat(CustomerId.of(uuid)).isNotNull();
        assertThat(ProductId.of(uuid)).isNotNull();
        assertThat(PaymentId.of(uuid)).isNotNull();
        assertThat(OrderItemId.of(uuid)).isNotNull();
    }

    @Test
    void typed_ids_to_string_returns_uuid_string() {
        var uuid = UUID.randomUUID();
        assertThat(new OrderId(uuid).toString()).isEqualTo(uuid.toString());
        assertThat(new CustomerId(uuid).toString()).isEqualTo(uuid.toString());
        assertThat(new ProductId(uuid).toString()).isEqualTo(uuid.toString());
        assertThat(new PaymentId(uuid).toString()).isEqualTo(uuid.toString());
        assertThat(new OrderItemId(uuid).toString()).isEqualTo(uuid.toString());
    }

    @Test
    void order_id_rejects_null() {
        assertThatThrownBy(() -> new OrderId(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void order_id_of_rejects_invalid_string() {
        assertThatThrownBy(() -> OrderId.of("not-a-uuid")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void typed_ids_are_equal_by_uuid_value() {
        var uuid = UUID.randomUUID();
        assertThat(new OrderId(uuid)).isEqualTo(new OrderId(uuid));
        assertThat(new CustomerId(uuid)).isEqualTo(new CustomerId(uuid));
        assertThat(new ProductId(uuid)).isEqualTo(new ProductId(uuid));
        assertThat(new PaymentId(uuid)).isEqualTo(new PaymentId(uuid));
        assertThat(new OrderItemId(uuid)).isEqualTo(new OrderItemId(uuid));
    }

    @Test
    void typed_ids_different_types_are_not_equal() {
        var uuid = UUID.randomUUID();
        assertThat(new OrderId(uuid)).isNotEqualTo(new CustomerId(uuid));
    }

    // ─── Enums ────────────────────────────────────────────────────────────────

    @Test
    void order_status_active_states() {
        assertThat(OrderStatus.CREATED.isActive()).isTrue();
        assertThat(OrderStatus.CONFIRMED.isActive()).isTrue();
        assertThat(OrderStatus.PAYMENT_PENDING.isActive()).isTrue();
        assertThat(OrderStatus.PAID.isActive()).isFalse();
        assertThat(OrderStatus.CANCELLED.isActive()).isFalse();
    }

    @Test
    void order_status_terminal_states() {
        assertThat(OrderStatus.PAID.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(OrderStatus.CREATED.isTerminal()).isFalse();
        assertThat(OrderStatus.CONFIRMED.isTerminal()).isFalse();
        assertThat(OrderStatus.PAYMENT_PENDING.isTerminal()).isFalse();
    }

    @Test
    void cancellation_reason_values_exist() {
        assertThat(CancellationReason.CUSTOMER_REQUEST).isNotNull();
        assertThat(CancellationReason.PAYMENT_ATTEMPTS_EXCEEDED).isNotNull();
    }

    @Test
    void payment_status_values_exist() {
        assertThat(PaymentStatus.PENDING).isNotNull();
        assertThat(PaymentStatus.APPROVED).isNotNull();
        assertThat(PaymentStatus.REJECTED).isNotNull();
        assertThat(PaymentStatus.CANCELLED).isNotNull();
    }

    // ─── ProductSnapshot ──────────────────────────────────────────────────────

    @Test
    void product_snapshot_stores_values() {
        var id    = new ProductId(UUID.randomUUID());
        var price = Money.of("199.90", "BRL");
        var snap  = new ProductSnapshot(id, "Teclado Mecânico TKL", price);

        assertThat(snap.productId()).isEqualTo(id);
        assertThat(snap.productName()).isEqualTo("Teclado Mecânico TKL");
        assertThat(snap.unitPrice()).isEqualTo(price);
    }

    @Test
    void product_snapshot_rejects_null_id() {
        assertThatThrownBy(() -> new ProductSnapshot(null, "Produto", Money.of("10.00", "BRL")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void product_snapshot_rejects_null_name() {
        var id = new ProductId(UUID.randomUUID());
        assertThatThrownBy(() -> new ProductSnapshot(id, null, Money.of("10.00", "BRL")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void product_snapshot_rejects_blank_name() {
        var id = new ProductId(UUID.randomUUID());
        assertThatThrownBy(() -> new ProductSnapshot(id, "  ", Money.of("10.00", "BRL")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void product_snapshot_rejects_null_price() {
        var id = new ProductId(UUID.randomUUID());
        assertThatThrownBy(() -> new ProductSnapshot(id, "Produto", null))
                .isInstanceOf(NullPointerException.class);
    }

    // ─── Eventos de domínio ────────────────────────────────────────────────────

    @Test
    void late_payment_result_received_occurred_at_is_not_null() {
        var paymentId = new PaymentId(UUID.randomUUID());
        var orderId   = new OrderId(UUID.randomUUID());
        var event     = new LatePaymentResultReceived(paymentId, orderId, "APPROVED");
        assertThat(event.occurredAt()).isNotNull();
    }
}
