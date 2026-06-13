package com.ecommerce.orders.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class QuantityTest {

    @Test
    void creates_quantity_with_positive_value() {
        assertThat(new Quantity(1).value()).isEqualTo(1);
        assertThat(new Quantity(100).value()).isEqualTo(100);
    }

    @Test
    void rejects_zero() {
        assertThatThrownBy(() -> new Quantity(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void rejects_negative() {
        assertThatThrownBy(() -> new Quantity(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void factory_of_creates_quantity() {
        assertThat(Quantity.of(3).value()).isEqualTo(3);
    }

    @Test
    void adds_two_quantities() {
        assertThat(Quantity.of(2).add(Quantity.of(3)).value()).isEqualTo(5);
    }

    @Test
    void add_returns_new_instance() {
        var q1 = Quantity.of(2);
        var q2 = Quantity.of(3);
        assertThat(q1.add(q2)).isNotSameAs(q1).isNotSameAs(q2);
    }

    @Test
    void equals_and_hashcode_by_value() {
        assertThat(Quantity.of(5)).isEqualTo(Quantity.of(5));
        assertThat(Quantity.of(5).hashCode()).isEqualTo(Quantity.of(5).hashCode());
        assertThat(Quantity.of(5)).isNotEqualTo(Quantity.of(6));
    }
}
