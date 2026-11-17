CREATE TABLE checkout_quote (
    id UUID PRIMARY KEY,
    owner UUID NOT NULL,
    email VARCHAR(320) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    lines JSONB NOT NULL,
    total_minor BIGINT NOT NULL CHECK (total_minor BETWEEN 0 AND 9007199254740991)
);

CREATE TABLE checkout_submission (
    id UUID PRIMARY KEY,
    owner UUID NOT NULL,
    quote_id UUID NOT NULL UNIQUE REFERENCES checkout_quote(id),
    idempotency_key UUID NOT NULL,
    state VARCHAR(24) NOT NULL CHECK (state IN ('PROCESSING', 'PLACED', 'REJECTED', 'RECONCILE_REQUIRED', 'COMPENSATING')),
    reason VARCHAR(32),
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    lease_until TIMESTAMPTZ,
    claim_version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (owner, idempotency_key),
    CHECK ((state = 'REJECTED') = (reason IS NOT NULL))
);

CREATE INDEX checkout_submission_due ON checkout_submission (next_attempt_at, id)
    WHERE state IN ('PROCESSING', 'COMPENSATING');
