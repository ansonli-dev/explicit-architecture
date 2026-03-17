package com.example.seedwork.application.command;

/**
 * Contract for all write-side command handlers.
 *
 * @param <C> the command type this handler accepts
 * @param <R> the return type (use {@link Void} for side-effect-only handlers)
 */
public interface CommandHandler<C extends Command<R>, R> {
    R handle(C command);
}
