package com.example.catalog.application.command.book;

import com.example.catalog.domain.model.CheckoutReservation;
import com.example.catalog.domain.model.CheckoutReservationBatch;
import com.example.seedwork.application.command.Command;

import java.util.UUID;

public record ReserveCheckoutCommand(UUID submissionId, CheckoutReservationBatch batch)
        implements Command<ReserveCheckoutCommand.Result> {

    public record Result(CheckoutReservation.State state, CheckoutReservation.Reason reason) {}
}
