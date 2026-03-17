package com.example.seedwork.application.query;

/**
 * Contract for all read-side query handlers.
 *
 * @param <Q> the query type this handler accepts
 * @param <R> the return type
 */
public interface QueryHandler<Q extends Query<R>, R> {
    R handle(Q query);
}
