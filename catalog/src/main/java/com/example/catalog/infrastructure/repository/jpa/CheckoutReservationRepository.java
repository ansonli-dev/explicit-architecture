package com.example.catalog.infrastructure.repository.jpa;

import com.example.catalog.domain.model.BookId;
import com.example.catalog.domain.model.CheckoutReservation;
import com.example.catalog.domain.ports.CheckoutReservationPersistence;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
class CheckoutReservationRepository implements CheckoutReservationPersistence {

    private final JdbcTemplate jdbc;

    @Override
    public Locked lockOrCreate(CheckoutReservation initial) {
        boolean created = jdbc.update("""
                insert into checkout_reservation(submission_id, fingerprint, state)
                values (?, ?, ?) on conflict (submission_id) do nothing
                """, initial.submissionId(), initial.fingerprint(), initial.state().name()) == 1;
        var record = jdbc.queryForObject("""
                select fingerprint, state, reason from checkout_reservation where submission_id = ? for update
                """, (row, index) -> {
                    String reason = row.getString("reason");
                    return new CheckoutReservation(initial.submissionId(), row.getString("fingerprint"),
                            CheckoutReservation.State.valueOf(row.getString("state")),
                            reason == null ? null : CheckoutReservation.Reason.valueOf(reason));
                }, initial.submissionId());
        return new Locked(Objects.requireNonNull(record), created);
    }

    @Override
    public void update(CheckoutReservation reservation) {
        jdbc.update("update checkout_reservation set state = ?, reason = ? where submission_id = ?",
                reservation.state().name(), reservation.reason() == null ? null : reservation.reason().name(),
                reservation.submissionId());
    }

    @Override
    public void storeHeldLines(CheckoutReservation reservation, List<CheckoutReservation.HeldLine> lines) {
        jdbc.batchUpdate("""
                insert into checkout_reservation_line(submission_id, book_id, quantity) values (?, ?, ?)
                """, lines, lines.size(), (statement, line) -> {
                    statement.setObject(1, reservation.submissionId());
                    statement.setObject(2, line.bookId().value());
                    statement.setInt(3, line.quantity());
                });
    }

    @Override
    public List<CheckoutReservation.HeldLine> heldLines(CheckoutReservation reservation) {
        return jdbc.query("""
                select book_id, quantity from checkout_reservation_line where submission_id = ? order by book_id
                """, (row, index) -> new CheckoutReservation.HeldLine(
                BookId.of(row.getObject("book_id", UUID.class)), row.getInt("quantity")), reservation.submissionId());
    }

    @Override
    public int activeQuantity(BookId bookId) {
        return Objects.requireNonNull(jdbc.queryForObject("""
                select coalesce(sum(line.quantity), 0) from checkout_reservation_line line
                join checkout_reservation reservation on reservation.submission_id = line.submission_id
                where line.book_id = ? and reservation.state = 'HELD'
                """, Integer.class, bookId.value()));
    }
}
