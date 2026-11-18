package com.example.order.infrastructure.repository.jpa;

import com.example.order.application.checkout.Checkout;
import com.example.order.application.checkout.CheckoutProblem;
import com.example.order.application.port.outbound.CheckoutStore;
import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.Order;
import com.example.order.domain.ports.OrderPersistence;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.example.order.application.checkout.CheckoutProblem.Code.*;

@Repository
@Profile("demo-integrated")
class CheckoutSubmissionRepository implements CheckoutStore {

    private static final Logger log = LoggerFactory.getLogger(CheckoutSubmissionRepository.class);
    private static final TypeReference<List<Checkout.Line>> LINES = new TypeReference<>() {};
    private static final String SUBMISSION_SELECT = """
            SELECT s.id, s.quote_id, s.state, s.reason, o.status AS order_status, o.total_cents, o.currency, o.created_at
            FROM checkout_submission s LEFT JOIN orders o ON o.id = s.id
            """;
    private static final String CURRENT_CLAIM = """
            WHERE id = :id AND state = :claimed_state AND claim_version = :fence AND lease_until > clock_timestamp()
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final OrderPersistence orders;

    CheckoutSubmissionRepository(JdbcClient jdbc, ObjectMapper json, OrderPersistence orders) {
        this.jdbc = jdbc;
        this.json = json;
        this.orders = orders;
    }

    @Override
    public void saveQuote(CustomerId owner, String email, Checkout.Quote quote) {
        jdbc.sql("""
                INSERT INTO checkout_quote (id, owner, email, expires_at, lines, total_minor)
                VALUES (:id, :owner, :email, :expires, CAST(:lines AS jsonb), :total)
                """)
                .param("id", quote.quoteId()).param("owner", owner.value()).param("email", email)
                .param("expires", Timestamp.from(quote.expiresAt())).param("lines", encode(quote.lines()))
                .param("total", quote.totalMinor()).update();
    }

    @Override
    public Checkout.Quote getQuote(CustomerId owner, UUID quoteId) {
        return jdbc.sql("""
                SELECT expires_at, lines, total_minor FROM checkout_quote WHERE id = :id AND owner = :owner
                """).param("id", quoteId).param("owner", owner.value())
                .query((rs, row) -> new Checkout.Quote(quoteId, rs.getTimestamp("expires_at").toInstant(),
                        decode(rs.getString("lines")), rs.getLong("total_minor"), Checkout.CURRENCY))
                .optional().orElseThrow(() -> new CheckoutProblem(NOT_FOUND));
    }

    @Override
    @Transactional
    public Checkout.Submission accept(CustomerId owner, UUID quoteId, UUID idempotencyKey, boolean intakeEnabled) {
        var prior = byKey(owner, idempotencyKey);
        if (prior.isPresent()) {
            return prior.get().replay(quoteId);
        }

        Instant expiresAt = jdbc.sql("""
                SELECT expires_at FROM checkout_quote WHERE id = :quote AND owner = :owner FOR UPDATE
                """).param("quote", quoteId).param("owner", owner.value())
                .query((rs, row) -> rs.getTimestamp(1).toInstant())
                .optional().orElseThrow(() -> new CheckoutProblem(NOT_FOUND));

        // A submission for this quote may have committed while we waited for its lock.
        prior = byKey(owner, idempotencyKey);
        if (prior.isPresent()) {
            return prior.get().replay(quoteId);
        }
        if (jdbc.sql("SELECT id FROM checkout_submission WHERE quote_id = :quote")
                .param("quote", quoteId).query(UUID.class).optional().isPresent()) {
            throw new CheckoutProblem(QUOTE_CONSUMED);
        }
        if (!intakeEnabled) {
            throw new CheckoutProblem(INTAKE_CLOSED);
        }
        if (!expiresAt.isAfter(Instant.now())) {
            throw new CheckoutProblem(QUOTE_EXPIRED);
        }

        UUID id = UUID.randomUUID();
        int inserted = jdbc.sql("""
                INSERT INTO checkout_submission (id, owner, quote_id, idempotency_key, state)
                VALUES (:id, :owner, :quote, :key, 'PROCESSING')
                ON CONFLICT (owner, idempotency_key) DO NOTHING
                """)
                .param("id", id).param("owner", owner.value()).param("quote", quoteId).param("key", idempotencyKey)
                .update();
        if (inserted == 0) {
            return byKey(owner, idempotencyKey).orElseThrow().replay(quoteId);
        }
        return new Checkout.Submission(id, Checkout.State.PROCESSING, null, null);
    }

    @Override
    public Checkout.Submission get(CustomerId owner, UUID submissionId) {
        return jdbc.sql(SUBMISSION_SELECT + "WHERE s.owner = :owner AND s.id = :id")
                .param("owner", owner.value()).param("id", submissionId).query(this::read).optional()
                .orElseThrow(() -> new CheckoutProblem(NOT_FOUND)).submission();
    }

    @Override
    @Transactional
    public Optional<Claim> claimDue() {
        jdbc.sql("""
                UPDATE checkout_submission SET state = 'RECONCILE_REQUIRED', lease_until = NULL
                WHERE state = 'PROCESSING' AND attempt_count >= :max AND lease_until <= clock_timestamp()
                RETURNING id
                """).param("max", MAX_ATTEMPTS).query(UUID.class).list().forEach(id ->
                log.warn("Checkout submission {} needs reconciliation after its last lease expired", id));

        return jdbc.sql("""
                WITH due AS (
                    SELECT id FROM checkout_submission
                    WHERE state IN ('PROCESSING', 'COMPENSATING')
                      AND next_attempt_at <= clock_timestamp()
                      AND (lease_until IS NULL OR lease_until <= clock_timestamp())
                    ORDER BY next_attempt_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
                )
                UPDATE checkout_submission AS s
                SET lease_until = clock_timestamp() + INTERVAL '10 seconds', claim_version = s.claim_version + 1,
                    attempt_count = LEAST(s.attempt_count + 1, :max)
                FROM due, checkout_quote q
                WHERE s.id = due.id AND q.id = s.quote_id
                RETURNING s.id, s.owner, q.email, q.lines, q.total_minor, s.claim_version, s.attempt_count, s.state
                """)
                .param("max", MAX_ATTEMPTS)
                .query((rs, row) -> new Claim(rs.getObject("id", UUID.class),
                        CustomerId.of(rs.getObject("owner", UUID.class)), rs.getString("email"),
                        decode(rs.getString("lines")), rs.getLong("total_minor"),
                        rs.getLong("claim_version"), rs.getInt("attempt_count"), "COMPENSATING".equals(rs.getString("state"))))
                .optional();
    }

    @Override
    @Transactional
    public boolean place(Claim claim, Order order) {
        if (!finish(claim, Checkout.State.PLACED, null)) {
            return false;
        }
        orders.save(order);
        return true;
    }

    @Override
    public boolean reject(Claim claim, Checkout.Rejection reason) {
        return finish(claim, Checkout.State.REJECTED, reason.name());
    }

    @Override
    public boolean retry(Claim claim) {
        if (!claim.compensating() && claim.attempt() >= MAX_ATTEMPTS) {
            return finish(claim, Checkout.State.RECONCILE_REQUIRED, null);
        }
        return currentClaim("""
                UPDATE checkout_submission
                SET lease_until = NULL, next_attempt_at = clock_timestamp() + :delay * INTERVAL '1 second'
                """, claim)
                .param("delay", 1 << Math.min(claim.attempt() - 1, 4)).update() == 1;
    }

    private boolean finish(Claim claim, Checkout.State state, String reason) {
        return currentClaim("""
                UPDATE checkout_submission SET state = :state, reason = :reason, lease_until = NULL
                """, claim).param("state", state.name()).param("reason", reason).update() == 1;
    }

    private JdbcClient.StatementSpec currentClaim(String update, Claim claim) {
        return jdbc.sql(update + CURRENT_CLAIM).param("id", claim.id()).param("fence", claim.fence())
                .param("claimed_state", claim.compensating() ? "COMPENSATING" : "PROCESSING");
    }

    private Optional<StoredSubmission> byKey(CustomerId owner, UUID key) {
        return jdbc.sql(SUBMISSION_SELECT + "WHERE s.owner = :owner AND s.idempotency_key = :key")
                .param("owner", owner.value()).param("key", key).query(this::read).optional();
    }

    private StoredSubmission read(ResultSet rs, int row) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String state = rs.getString("state");
        var externalState = "COMPENSATING".equals(state) ? Checkout.State.RECONCILE_REQUIRED : Checkout.State.valueOf(state);
        Checkout.PlacedOrder order = null;
        if (externalState == Checkout.State.PLACED) {
            var createdAt = rs.getTimestamp("created_at");
            order = new Checkout.PlacedOrder(id, rs.getString("order_status"), rs.getLong("total_cents"),
                    rs.getString("currency"), createdAt == null ? null : createdAt.toInstant());
        }
        String reason = rs.getString("reason");
        return new StoredSubmission(rs.getObject("quote_id", UUID.class),
                new Checkout.Submission(id, externalState, order, reason == null ? null : Checkout.Rejection.valueOf(reason)));
    }

    private String encode(List<Checkout.Line> lines) {
        try {
            return json.writeValueAsString(lines);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write checkout lines", e);
        }
    }

    private List<Checkout.Line> decode(String lines) {
        try {
            return json.readValue(lines, LINES);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read checkout lines", e);
        }
    }

    private record StoredSubmission(UUID quoteId, Checkout.Submission submission) {
        Checkout.Submission replay(UUID requestedQuote) {
            if (!quoteId.equals(requestedQuote)) {
                throw new CheckoutProblem(IDEMPOTENCY_CONFLICT);
            }
            return submission;
        }
    }
}
