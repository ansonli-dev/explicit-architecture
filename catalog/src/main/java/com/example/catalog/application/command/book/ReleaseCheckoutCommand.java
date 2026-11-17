package com.example.catalog.application.command.book;

import com.example.seedwork.application.command.Command;

import java.util.UUID;

public record ReleaseCheckoutCommand(UUID submissionId) implements Command<ReleaseCheckoutCommand.Result> {
    public enum Result { RELEASED, ALREADY_RELEASED, MISSING }
}
