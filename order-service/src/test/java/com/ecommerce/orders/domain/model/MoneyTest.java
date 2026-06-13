package com.ecommerce.orders.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class MoneyTest {

    @Test
    void creates_money_with_valid_values() {
        var money = new Money(new BigDecimal("199.90"), "BRL");
        assertThat(money.amount()).isEqualByComparingTo("199.90");
        assertThat(money.currency()).isEqualTo("BRL");
    }

    @Test
    void normalizes_currency_to_uppercase() {
        var money = new Money(new BigDecimal("10.00"), "brl");
        assertThat(money.currency()).isEqualTo("BRL");
    }

    @Test
    void normalizes_scale_to_two_decimal_places() {
        var money = new Money(new BigDecimal("199.9"), "BRL");
        assertThat(money.amount()).isEqualByComparingTo("199.90");
        assertThat(money.amount().scale()).isEqualTo(2);
    }

    @Test
    void factory_method_of_creates_money_from_string() {
        var money = Money.of("49.90", "BRL");
        assertThat(money.amount()).isEqualByComparingTo("49.90");
        assertThat(money.currency()).isEqualTo("BRL");
    }

    @Test
    void rejects_null_amount() {
        assertThatThrownBy(() -> new Money(null, "BRL"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejects_null_currency() {
        assertThatThrownBy(() -> new Money(BigDecimal.TEN, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejects_blank_currency() {
        assertThatThrownBy(() -> new Money(BigDecimal.TEN, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_negative_amount() {
        assertThatThrownBy(() -> new Money(new BigDecimal("-0.01"), "BRL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void allows_zero_amount() {
        assertThatCode(() -> new Money(BigDecimal.ZERO, "BRL")).doesNotThrowAnyException();
    }

    @Test
    void adds_money_with_same_currency() {
        var a = Money.of("199.90", "BRL");
        var b = Money.of("49.90", "BRL");
        assertThat(a.add(b).amount()).isEqualByComparingTo("249.80");
    }

    @Test
    void add_returns_new_instance() {
        var a = Money.of("100.00", "BRL");
        var b = Money.of("50.00", "BRL");
        assertThat(a.add(b)).isNotSameAs(a).isNotSameAs(b);
    }

    @Test
    void rejects_add_with_different_currency() {
        var brl = Money.of("100.00", "BRL");
        var usd = Money.of("100.00", "USD");
        assertThatThrownBy(() -> brl.add(usd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currencies");
    }

    @Test
    void multiplies_by_quantity() {
        var price = Money.of("199.90", "BRL");
        assertThat(price.multiply(2).amount()).isEqualByComparingTo("399.80");
    }

    @Test
    void multiply_by_zero_gives_zero() {
        var price = Money.of("199.90", "BRL");
        assertThat(price.multiply(0).amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void calculates_order_total_correctly() {
        // 2 × 199.90 + 1 × 49.90 = 449.70 (cenário Gherkin de confirmação)
        var teclado = Money.of("199.90", "BRL").multiply(2);
        var mouse   = Money.of("49.90", "BRL").multiply(1);
        assertThat(teclado.add(mouse).amount()).isEqualByComparingTo("449.70");
    }

    @Test
    void equals_considers_amount_and_currency() {
        var a = new Money(new BigDecimal("10.00"), "BRL");
        var b = new Money(new BigDecimal("10.0"), "brl");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void not_equal_when_different_currency() {
        assertThat(Money.of("10.00", "BRL")).isNotEqualTo(Money.of("10.00", "USD"));
    }

    @Test
    void to_string_shows_amount_and_currency() {
        assertThat(Money.of("199.90", "BRL").toString()).contains("199.90").contains("BRL");
    }
}
