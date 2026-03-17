package com.example.seedwork.infrastructure.bus;

import com.example.seedwork.application.bus.QueryBus;
import com.example.seedwork.application.query.Query;
import com.example.seedwork.application.query.QueryHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ResolvableType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Spring-backed QueryBus that auto-discovers all {@link QueryHandler} beans
 * and dispatches queries to the matching handler by query class type.
 *
 * <p>Cross-cutting concerns handled here:
 * <ul>
 *   <li>Structured logging of dispatch start, completion, and failure</li>
 *   <li>Execution time measurement via {@code seedwork.query.duration} (tag: query, outcome)</li>
 * </ul>
 */
@Slf4j
public class SpringQueryBus implements QueryBus {

    private final Map<Class<?>, QueryHandler<?, ?>> handlers;
    private final MeterRegistry meterRegistry;

    public SpringQueryBus(List<QueryHandler<?, ?>> handlerList, MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        handlers = new HashMap<>();
        for (QueryHandler<?, ?> handler : handlerList) {
            ResolvableType handlerType = ResolvableType.forClass(handler.getClass())
                    .as(QueryHandler.class);
            Class<?> queryType = handlerType.getGeneric(0).resolve();
            if (queryType != null) {
                handlers.put(queryType, handler);
                log.debug("[QueryBus] registered handler {} → {}", queryType.getSimpleName(),
                        handler.getClass().getSimpleName());
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> R dispatch(Query<R> query) {
        String queryName = query.getClass().getSimpleName();
        QueryHandler<Query<R>, R> handler =
                (QueryHandler<Query<R>, R>) handlers.get(query.getClass());

        if (handler == null) {
            throw new IllegalArgumentException("No QueryHandler registered for: " + queryName);
        }

        log.info("[QueryBus] → dispatching {}", queryName);
        long startNs = System.nanoTime();
        String outcome = "success";
        try {
            R result = handler.handle(query);
            log.info("[QueryBus] ✓ {} completed in {}ms", queryName,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs));
            return result;
        } catch (Exception e) {
            outcome = "error";
            log.error("[QueryBus] ✗ {} failed in {}ms — {}: {}",
                    queryName, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs),
                    e.getClass().getSimpleName(), e.getMessage());
            throw e;
        } finally {
            Timer.builder("seedwork.query.duration")
                    .tag("query", queryName)
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .record(System.nanoTime() - startNs, TimeUnit.NANOSECONDS);
        }
    }
}
