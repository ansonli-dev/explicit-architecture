package com.example.catalog.interfaces.rest.v2;

import com.example.catalog.application.CatalogDataException;
import com.example.catalog.application.command.book.ReleaseCheckoutCommand;
import com.example.catalog.application.command.book.ReserveCheckoutCommand;
import com.example.catalog.application.query.book.CheckoutSnapshotQuery;
import com.example.catalog.domain.model.BookId;
import com.example.catalog.domain.model.CheckoutReservation;
import com.example.catalog.domain.model.CheckoutReservationBatch;
import com.example.seedwork.application.bus.CommandBus;
import com.example.seedwork.application.bus.QueryBus;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Profile("demo-integrated")
@RestController
@RequestMapping("/api/v2/internal/checkout")
@RequiredArgsConstructor
class CheckoutInventoryController {

    private final QueryBus queries;
    private final CommandBus commands;

    @PostMapping("/snapshots")
    SnapshotResponse snapshots(@Valid @RequestBody SnapshotRequest request) {
        var query = new CheckoutSnapshotQuery(request.lines().stream()
                .map(line -> new CheckoutSnapshotQuery.Line(line.bookId(), line.quantity()))
                .toList());
        return new SnapshotResponse(queries.dispatch(query).stream()
                .map(line -> new SnapshotLineResponse(line.bookId(), line.title(), line.quantity(),
                        line.unitPriceMinor(), line.currency(), line.stock()))
                .toList());
    }

    @PutMapping("/reservations/{submissionId}")
    ReservationResponse reserve(@PathVariable UUID submissionId, @Valid @RequestBody ReserveRequest request) {
        var batch = new CheckoutReservationBatch(request.lines().stream()
                .map(line -> new CheckoutReservationBatch.Line(BookId.of(line.bookId()), line.quantity(),
                        line.unitPriceMinor(), line.currency()))
                .toList());
        var result = commands.dispatch(new ReserveCheckoutCommand(submissionId, batch));
        return new ReservationResponse(result.state(), result.reason());
    }

    @DeleteMapping("/reservations/{submissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void release(@PathVariable UUID submissionId) {
        commands.dispatch(new ReleaseCheckoutCommand(submissionId));
    }

    record SnapshotRequest(@NotNull @Size(min = 1, max = 20) List<@NotNull @Valid SnapshotLine> lines) {}

    record SnapshotLine(@NotNull UUID bookId, @NotNull @Min(1) @Max(99) Integer quantity) {}

    record SnapshotResponse(List<SnapshotLineResponse> lines) {}

    record SnapshotLineResponse(UUID bookId, String title, int quantity, long unitPriceMinor,
                                String currency, int stock) {
        SnapshotLineResponse {
            CatalogDataException.checkPrice(unitPriceMinor, currency);
        }
    }

    record ReserveRequest(@NotNull @Size(min = 1, max = 20) List<@NotNull @Valid ReserveLine> lines) {}

    record ReserveLine(@NotNull UUID bookId, @NotNull @Min(1) @Max(99) Integer quantity,
                       @NotNull @Min(0) @Max(CatalogDataException.MAX_SAFE_INTEGER) Long unitPriceMinor,
                       @NotNull @Pattern(regexp = "[A-Z]{3}") String currency) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ReservationResponse(CheckoutReservation.State state, CheckoutReservation.Reason reason) {}
}
