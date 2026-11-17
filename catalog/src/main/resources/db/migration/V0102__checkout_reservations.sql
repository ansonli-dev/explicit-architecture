CREATE TABLE checkout_reservation (
    submission_id UUID PRIMARY KEY,
    fingerprint VARCHAR(64),
    state VARCHAR(12) NOT NULL CHECK (state IN ('PENDING', 'HELD', 'REJECTED', 'RELEASED')),
    reason VARCHAR(24),
    CHECK (state = 'RELEASED' OR fingerprint IS NOT NULL),
    CHECK ((state = 'REJECTED') = (reason IS NOT NULL))
);

CREATE TABLE checkout_reservation_line (
    submission_id UUID NOT NULL REFERENCES checkout_reservation(submission_id),
    book_id UUID NOT NULL REFERENCES book(id),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    PRIMARY KEY (submission_id, book_id)
);

CREATE INDEX checkout_reservation_line_book ON checkout_reservation_line (book_id);
