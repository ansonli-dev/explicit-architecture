package com.example.seedwork.infrastructure.bus;

import com.example.seedwork.application.bus.CommandBus;
import com.example.seedwork.application.command.Command;
import com.example.seedwork.application.command.CommandHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ResolvableType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Spring-backed CommandBus that auto-discovers all {@link CommandHandler} beans
 * and dispatches commands to the matching handler by command class type.
 *
 * <p>Cross-cutting concerns handled here (so individual handlers stay focused):
 * <ul>
 *   <li>Structured logging of dispatch start, completion, and failure</li>
 *   <li>Execution time measurement via {@code seedwork.command.duration} (tag: command, outcome)</li>
 * </ul>
 */
@Slf4j
public class SpringCommandBus implements CommandBus {

    private final Map<Class<?>, CommandHandler<?, ?>> handlers;
    private final MeterRegistry meterRegistry;

    public SpringCommandBus(List<CommandHandler<?, ?>> handlerList, MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        handlers = new HashMap<>();
        for (CommandHandler<?, ?> handler : handlerList) {
            ResolvableType handlerType = ResolvableType.forClass(handler.getClass())
                    .as(CommandHandler.class);
            Class<?> commandType = handlerType.getGeneric(0).resolve();
            if (commandType != null) {
                handlers.put(commandType, handler);
                log.debug("[CommandBus] registered handler {} → {}", commandType.getSimpleName(),
                        handler.getClass().getSimpleName());
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> R dispatch(Command<R> command) {
        String commandName = command.getClass().getSimpleName();
        CommandHandler<Command<R>, R> handler =
                (CommandHandler<Command<R>, R>) handlers.get(command.getClass());

        if (handler == null) {
            throw new IllegalArgumentException("No CommandHandler registered for: " + commandName);
        }

        log.info("[CommandBus] → dispatching {}", commandName);
        long startNs = System.nanoTime();
        String outcome = "success";
        try {
            R result = handler.handle(command);
            log.info("[CommandBus] ✓ {} completed in {}ms", commandName,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs));
            return result;
        } catch (Exception e) {
            outcome = "error";
            log.error("[CommandBus] ✗ {} failed in {}ms — {}: {}",
                    commandName, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs),
                    e.getClass().getSimpleName(), e.getMessage());
            throw e;
        } finally {
            Timer.builder("seedwork.command.duration")
                    .tag("command", commandName)
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .record(System.nanoTime() - startNs, TimeUnit.NANOSECONDS);
        }
    }
}
