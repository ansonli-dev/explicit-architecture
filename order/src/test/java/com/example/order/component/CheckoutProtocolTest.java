package com.example.order.component;

import com.example.order.application.checkout.Checkout;
import com.example.order.application.checkout.CheckoutMessages;
import com.example.order.application.checkout.CheckoutProblem;
import com.example.order.application.checkout.ResolveCheckoutSubmission;
import com.example.order.application.command.order.CancelOwnedOrderCommand;
import com.example.order.application.port.outbound.CheckoutCatalog;
import com.example.order.application.port.outbound.CheckoutStore;
import com.example.order.application.port.outbound.OrderSearchRepository;
import com.example.order.application.query.order.MyOrders;
import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.Money;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderId;
import com.example.seedwork.application.bus.CommandBus;
import com.example.seedwork.application.bus.QueryBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.order.application.checkout.CheckoutProblem.Code.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///checkout",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.data.elasticsearch.repositories.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.properties.schema.registry.url=mock://checkout-protocol",
        "spring.kafka.properties.auto.register.schemas=true",
        "outbox.relay.strategy=debezium",
        "demo.checkout.worker-enabled=false",
        "demo.jwt.issuer=http://localhost:18180/realms/bookstore",
        "demo.jwt.jwk-set-uri=http://localhost:18180/realms/bookstore/protocol/openid-connect/certs",
        "demo.catalog.base-url=http://localhost:18081",
        "demo.catalog.token-url=http://localhost:18180/realms/bookstore/protocol/openid-connect/token",
        "demo.catalog.client-secret=checkout-test"
})
@ActiveProfiles("demo-integrated")
@ExtendWith(OutputCaptureExtension.class)
class CheckoutProtocolTest {

    private static final CustomerId OWNER = CustomerId.of(UUID.randomUUID());
    private static final UUID FIRST = UUID.fromString("00000000-0000-4000-8000-000000000101");
    private static final UUID SECOND = UUID.fromString("00000000-0000-4000-8000-000000000102");

    @Autowired CommandBus commands;
    @Autowired QueryBus queries;
    @Autowired CheckoutStore submissions;
    @Autowired ResolveCheckoutSubmission resolver;
    @Autowired JdbcTemplate jdbc;
    @MockBean CheckoutCatalog catalog;
    @MockBean OrderSearchRepository search;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("DELETE FROM checkout_submission");
        jdbc.update("DELETE FROM checkout_quote");
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM orders");
        when(catalog.snapshot(anyList())).thenAnswer(call -> {
            List<Checkout.RequestedLine> items = call.getArgument(0);
            return items.stream().map(item -> new Checkout.Snapshot(item.bookId(), "First", item.quantity(),
                    4_900, "CNY", 10)).toList();
        });
    }

    @Test
    void quoteUsesFrozenPricesAndRejectsInvalidCurrencyAndAmounts() {
        when(catalog.snapshot(anyList())).thenReturn(List.of(
                new Checkout.Snapshot(SECOND, "Second", 1, 31_000, "CNY", 10),
                new Checkout.Snapshot(FIRST, "First", 5, 4_900, "CNY", 10)));
        var quote = commands.dispatch(new CheckoutMessages.QuoteOrder(OWNER, "reader@example.test", List.of(
                new Checkout.RequestedLine(SECOND, 1), new Checkout.RequestedLine(FIRST, 5))));
        assertThat(quote.totalMinor()).isEqualTo(50_397);
        assertThat(quote.lines()).extracting(Checkout.Line::bookId).containsExactly(FIRST, SECOND);

        when(catalog.snapshot(anyList())).thenReturn(List.of(new Checkout.Snapshot(FIRST, "USD", 1, 1, "USD", 10)));
        assertCode(() -> quote(1), UNSUPPORTED_CURRENCY);
        when(catalog.snapshot(anyList())).thenReturn(List.of(
                new Checkout.Snapshot(FIRST, "Expensive", 2, 9_007_199_254_740_991L, "CNY", 10)));
        assertCode(() -> quote(2), AMOUNT_OUT_OF_RANGE);
    }

    @Test
    void quoteWriteFailureHidesBuyerEmailInExceptionAndApplicationLogs(CapturedOutput logs) {
        jdbc.execute("ALTER TABLE checkout_quote ADD CONSTRAINT reject_quote_test CHECK (false)");
        try {
            assertThatThrownBy(() -> quote(1)).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageNotContaining("reader@example.test");
            assertThat(logs.getAll()).doesNotContain("reader@example.test");
        } finally {
            jdbc.execute("ALTER TABLE checkout_quote DROP CONSTRAINT reject_quote_test");
        }
    }

    @Test
    void concurrentReplayReturnsOneIdAndSurvivesExpiryAndIntakeClosure() throws Exception {
        var quote = quote(1);
        var key = UUID.randomUUID();
        var start = new CountDownLatch(1);
        UUID accepted;
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = threads.submit(() -> { start.await(); return submit(quote, key); });
            var second = threads.submit(() -> { start.await(); return submit(quote, key); });
            start.countDown();
            accepted = first.get().submissionId();
            assertThat(second.get().submissionId()).isEqualTo(accepted);
        }
        jdbc.update("UPDATE checkout_quote SET expires_at = now() - interval '1 second' WHERE id = ?", quote.quoteId());
        assertThat(submissions.accept(OWNER, quote.quoteId(), key, false).submissionId()).isEqualTo(accepted);
        assertCode(() -> submissions.accept(OWNER, quote.quoteId(), UUID.randomUUID(), true), QUOTE_CONSUMED);
        assertCode(() -> submissions.accept(OWNER, quote(1).quoteId(), key, false), IDEMPOTENCY_CONFLICT);
        assertCode(() -> submissions.accept(OWNER, quote(1).quoteId(), UUID.randomUUID(), false), INTAKE_CLOSED);
        assertCode(() -> submissions.get(CustomerId.of(UUID.randomUUID()), accepted), NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM checkout_submission", Long.class)).isEqualTo(1L);
    }

    @Test
    void lostReservationResponseRecoversSameOrderAndOutboxWithAuthoritativeOwnerCancellation() {
        var quote = quote(1);
        var key = UUID.randomUUID();
        var id = submit(quote, key).submissionId();
        var holds = new AtomicInteger();
        when(catalog.reserve(eq(id), eq(quote.lines()))).thenAnswer(invocation -> {
            if (holds.getAndIncrement() == 0) {
                throw new CheckoutProblem(CATALOG_UNAVAILABLE);
            }
            return new Checkout.Reservation(Checkout.ReservationState.HELD, null);
        });
        resolver.processNext();
        assertThat(submissions.get(OWNER, id).state()).isEqualTo(Checkout.State.PROCESSING);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Long.class)).isZero();
        jdbc.update("UPDATE checkout_submission SET next_attempt_at = now() WHERE id = ?", id);
        resolver.processNext();
        resolver.processNext();
        verify(catalog, times(2)).reserve(id, quote.lines());
        assertThat(submissions.accept(OWNER, quote.quoteId(), key, false).order().orderId()).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT reservation_protocol FROM orders WHERE id=?", Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_id=?", Long.class, id)).isEqualTo(1L);
        byte[] event = jdbc.queryForObject("SELECT avro_payload FROM outbox_event WHERE aggregate_id=?", byte[].class, id);
        assertThat(new String(event, StandardCharsets.UTF_8)).doesNotContain("reader@example.test");

        assertCode(() -> queries.dispatch(new MyOrders.Detail(CustomerId.of(UUID.randomUUID()), OrderId.of(id))), NOT_FOUND);
        var detail = queries.dispatch(new MyOrders.Detail(OWNER, OrderId.of(id)));
        assertThat(detail.version()).isZero();
        assertThat(detail.lines()).isEqualTo(quote.lines());
        commands.dispatch(new CancelOwnedOrderCommand(OWNER, OrderId.of(id), "Changed plans", detail.version()));
        assertCode(() -> commands.dispatch(new CancelOwnedOrderCommand(OWNER, OrderId.of(id), "Stale", detail.version())), VERSION_CONFLICT);
        assertThat(queries.dispatch(new MyOrders.Detail(OWNER, OrderId.of(id))).status()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_id=?", Long.class, id)).isEqualTo(2L);
    }

    @Test
    void expiredLeaseAndCompensationFenceBlockLatePlacement() {
        var id = submit(quote(1), UUID.randomUUID()).submissionId();
        var stale = submissions.claimDue().orElseThrow();
        jdbc.update("UPDATE checkout_submission SET lease_until = now() - interval '1 second' WHERE id = ?", id);
        var current = submissions.claimDue().orElseThrow();
        assertThat(current.fence()).isGreaterThan(stale.fence());
        assertThat(submissions.reject(stale, Checkout.Rejection.INSUFFICIENT_STOCK)).isFalse();

        jdbc.update("UPDATE checkout_submission SET state='COMPENSATING', lease_until=NULL, claim_version=claim_version+1 WHERE id=?", id);
        var lateOrder = Order.createWithCheckoutReservation(OrderId.of(id), current.owner(), current.email(),
                current.lines().stream().map(Checkout.Line::toOrderItem).toList(), new Money(current.totalMinor(), "CNY"));
        lateOrder.place();
        assertThat(submissions.place(current, lateOrder)).isFalse();
        doThrow(new CheckoutProblem(CATALOG_UNAVAILABLE)).doNothing().when(catalog).release(id);
        resolver.processNext();
        assertThat(submissions.get(OWNER, id).state()).isEqualTo(Checkout.State.RECONCILE_REQUIRED);
        jdbc.update("UPDATE checkout_submission SET next_attempt_at = now() WHERE id=?", id);
        resolver.processNext();
        verify(catalog, times(2)).release(id);
        assertThat(submissions.get(OWNER, id).reason()).isEqualTo(Checkout.Rejection.PLACEMENT_ABORTED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Long.class)).isZero();
    }

    @Test
    void exhaustedAttemptsAreDurableAndOperatorCanResumeTheSameReservation() {
        var quote = quote(1);
        var id = submit(quote, UUID.randomUUID()).submissionId();
        when(catalog.reserve(id, quote.lines())).thenThrow(new CheckoutProblem(CATALOG_UNAVAILABLE));
        for (int i = 0; i < CheckoutStore.MAX_ATTEMPTS; i++) {
            resolver.processNext();
            jdbc.update("UPDATE checkout_submission SET next_attempt_at = now() WHERE id=?", id);
        }
        assertThat(submissions.get(OWNER, id).state()).isEqualTo(Checkout.State.RECONCILE_REQUIRED);
        assertThat(submissions.claimDue()).isEmpty();

        jdbc.update("""
                UPDATE checkout_submission SET state='PROCESSING', claim_version=claim_version+1, attempt_count=0
                WHERE id=? AND state='RECONCILE_REQUIRED'
                """, id);
        doReturn(new Checkout.Reservation(Checkout.ReservationState.HELD, null)).when(catalog).reserve(id, quote.lines());
        resolver.processNext();
        assertThat(submissions.get(OWNER, id).order().orderId()).isEqualTo(id);
    }

    private Checkout.Quote quote(int quantity) {
        return commands.dispatch(new CheckoutMessages.QuoteOrder(OWNER, "reader@example.test",
                List.of(new Checkout.RequestedLine(FIRST, quantity))));
    }

    private Checkout.Submission submit(Checkout.Quote quote, UUID key) {
        return commands.dispatch(new CheckoutMessages.SubmitOrder(OWNER, quote.quoteId(), key));
    }

    private static void assertCode(Runnable action, CheckoutProblem.Code code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CheckoutProblem.class,
                problem -> assertThat(problem.code()).isEqualTo(code));
    }
}
