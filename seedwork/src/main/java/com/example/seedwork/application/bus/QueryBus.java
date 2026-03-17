package com.example.seedwork.application.bus;

import com.example.seedwork.application.query.Query;

/**
 * Single entry point for all read-side operations.
 * <p>
 * Controllers and other primary adapters depend only on this interface —
 * they never import concrete handler classes.
 */
public interface QueryBus {
    <R> R dispatch(Query<R> query);
}
