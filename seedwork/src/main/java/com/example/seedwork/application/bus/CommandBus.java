package com.example.seedwork.application.bus;

import com.example.seedwork.application.command.Command;

/**
 * Single entry point for all write-side operations.
 * <p>
 * Controllers and other primary adapters depend only on this interface —
 * they never import concrete handler classes.
 */
public interface CommandBus {
    <R> R dispatch(Command<R> command);
}
