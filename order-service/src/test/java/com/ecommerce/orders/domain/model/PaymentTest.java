package com.ecommerce.orders.domain.model;

import com.ecommerce.orders.domain.event.PaymentApproved;
import com.ecommerce.orders.domain.event.PaymentCancelled;
import com.ecommerce.orders.domain.event.PaymentCreated;
import com.ecommerce.orders.domain.event.PaymentRejected;
import com.ecommerce.orders.domain.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Payment aggregate")
class PaymentTest {

    private static final PaymentId PAYMENT_ID = PaymentId.generate();
    private static final OrderId ORDER_ID = OrderId.generate();
    private static final String CARD_TOKEN = "tok-approved";

    // ── Factory ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("cria pagamento PENDING com attemptNumber=1 e dispara PaymentCreated")
        void createsPendingPaymentWithAttemptOne() {
            var payment = Payment.create(PAYMENT_ID, ORDER_ID, CARD_TOKEN, 1);

            assertThat(payment.getId()).isEqualTo(PAYMENT_ID);
            assertThat(payment.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(payment.getCardToken()).isEqualTo(CARD_TOKEN);
            assertThat(payment.getAttemptNumber()).isEqualTo(1);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);

            var events = payment.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(PaymentCreated.class);
            var evt = (PaymentCreated) events.get(0);
            assertThat(evt.paymentId()).isEqualTo(PAYMENT_ID);
            assertThat(evt.orderId()).isEqualTo(ORDER_ID);
            assertThat(evt.attemptNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("lanca excecao quando id e nulo")
        void throwsOnNullId() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Payment.create(null, ORDER_ID, CARD_TOKEN, 1));
        }

        @Test
        @DisplayName("lanca excecao quando orderId e nulo")
        void throwsOnNullOrderId() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Payment.create(PAYMENT_ID, null, CARD_TOKEN, 1));
        }

        @Test
        @DisplayName("lanca excecao quando cardToken e nulo ou vazio")
        void throwsOnBlankCardToken() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Payment.create(PAYMENT_ID, ORDER_ID, null, 1));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Payment.create(PAYMENT_ID, ORDER_ID, "  ", 1));
        }

        @Test
        @DisplayName("lanca excecao quando attemptNumber < 1")
        void throwsOnInvalidAttemptNumber() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Payment.create(PAYMENT_ID, ORDER_ID, CARD_TOKEN, 0));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Payment.create(PAYMENT_ID, ORDER_ID, CARD_TOKEN, -1));
        }
    }

    // ── approve() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("approve()")
    class Approve {

        @Test
        @DisplayName("PENDING → APPROVED dispara PaymentApproved")
        void pendingToApproved() {
            var payment = pendingPayment();

            payment.approve();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            var events = payment.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(PaymentApproved.class);
        }

        @Test
        @DisplayName("APPROVED → APPROVED e idempotente (sem excecao, sem evento extra)")
        void approvedIsIdempotent() {
            var payment = pendingPayment();
            payment.approve();
            payment.pullDomainEvents(); // limpa

            payment.approve(); // segunda chamada

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(payment.pullDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("REJECTED → APPROVED lanca InvalidStateTransitionException")
        void rejectedCannotBeApproved() {
            var payment = pendingPayment();
            payment.reject();

            assertThatExceptionOfType(InvalidStateTransitionException.class)
                    .isThrownBy(payment::approve)
                    .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo("invalid-payment-state"));
        }

        @Test
        @DisplayName("CANCELLED → APPROVED lanca InvalidStateTransitionException")
        void cancelledCannotBeApproved() {
            var payment = pendingPayment();
            payment.cancel();

            assertThatExceptionOfType(InvalidStateTransitionException.class)
                    .isThrownBy(payment::approve)
                    .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo("invalid-payment-state"));
        }
    }

    // ── reject() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("reject()")
    class Reject {

        @Test
        @DisplayName("PENDING → REJECTED dispara PaymentRejected")
        void pendingToRejected() {
            var payment = pendingPayment();

            payment.reject();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REJECTED);
            var events = payment.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(PaymentRejected.class);
            var evt = (PaymentRejected) events.get(0);
            assertThat(evt.paymentId()).isEqualTo(PAYMENT_ID);
            assertThat(evt.orderId()).isEqualTo(ORDER_ID);
        }

        @Test
        @DisplayName("REJECTED → REJECTED lanca InvalidStateTransitionException")
        void rejectedIsNotIdempotent() {
            var payment = pendingPayment();
            payment.reject();

            assertThatExceptionOfType(InvalidStateTransitionException.class)
                    .isThrownBy(payment::reject)
                    .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo("invalid-payment-state"));
        }

        @Test
        @DisplayName("APPROVED → REJECTED lanca InvalidStateTransitionException")
        void approvedCannotBeRejected() {
            var payment = pendingPayment();
            payment.approve();

            assertThatExceptionOfType(InvalidStateTransitionException.class)
                    .isThrownBy(payment::reject)
                    .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo("invalid-payment-state"));
        }

        @Test
        @DisplayName("CANCELLED → REJECTED lanca InvalidStateTransitionException")
        void cancelledCannotBeRejected() {
            var payment = pendingPayment();
            payment.cancel();

            assertThatExceptionOfType(InvalidStateTransitionException.class)
                    .isThrownBy(payment::reject);
        }
    }

    // ── cancel() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancel()")
    class Cancel {

        @Test
        @DisplayName("PENDING → CANCELLED dispara PaymentCancelled")
        void pendingToCancelled() {
            var payment = pendingPayment();

            payment.cancel();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            var events = payment.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(PaymentCancelled.class);
        }

        @Test
        @DisplayName("CANCELLED → CANCELLED e idempotente (sem excecao, sem evento extra)")
        void cancelledIsIdempotent() {
            var payment = pendingPayment();
            payment.cancel();
            payment.pullDomainEvents();

            payment.cancel();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(payment.pullDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("APPROVED → CANCELLED lanca InvalidStateTransitionException")
        void approvedCannotBeCancelled() {
            var payment = pendingPayment();
            payment.approve();

            assertThatExceptionOfType(InvalidStateTransitionException.class)
                    .isThrownBy(payment::cancel)
                    .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo("payment-not-cancellable"));
        }

        @Test
        @DisplayName("REJECTED → CANCELLED lanca InvalidStateTransitionException")
        void rejectedCannotBeCancelled() {
            var payment = pendingPayment();
            payment.reject();

            assertThatExceptionOfType(InvalidStateTransitionException.class)
                    .isThrownBy(payment::cancel)
                    .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo("payment-not-cancellable"));
        }
    }

    // ── pullDomainEvents() ────────────────────────────────────────────────────

    @Nested
    @DisplayName("pullDomainEvents()")
    class PullDomainEvents {

        @Test
        @DisplayName("limpa a lista apos retornar os eventos")
        void clearsEventsAfterPull() {
            var payment = Payment.create(PAYMENT_ID, ORDER_ID, CARD_TOKEN, 1);
            assertThat(payment.pullDomainEvents()).hasSize(1); // PaymentCreated
            assertThat(payment.pullDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("retorna lista imutavel")
        void returnsImmutableList() {
            var payment = pendingPayment();
            var events = payment.pullDomainEvents();
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> events.add(null));
        }
    }

    // ── reconstitute() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("reconstitute()")
    class Reconstitute {

        @Test
        @DisplayName("reconstitui pagamento sem disparar eventos")
        void reconstitutesWithoutEvents() {
            var payment = Payment.reconstitute(PAYMENT_ID, ORDER_ID, CARD_TOKEN,
                    PaymentStatus.APPROVED, 2);

            assertThat(payment.getId()).isEqualTo(PAYMENT_ID);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(payment.getAttemptNumber()).isEqualTo(2);
            assertThat(payment.pullDomainEvents()).isEmpty();
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Payment pendingPayment() {
        var payment = Payment.create(PAYMENT_ID, ORDER_ID, CARD_TOKEN, 1);
        payment.pullDomainEvents(); // limpa PaymentCreated para os testes focarem nas transicoes
        return payment;
    }
}
