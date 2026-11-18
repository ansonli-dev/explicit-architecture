package com.example.order.interfaces.rest.v2;

import com.example.order.application.checkout.Checkout;
import com.example.order.application.command.order.CancelOwnedOrderCommand;
import com.example.order.application.query.order.MyOrders;
import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.OrderId;
import com.example.order.domain.ports.OrderPersistence.Cancellation;
import com.example.seedwork.application.bus.CommandBus;
import com.example.seedwork.application.bus.QueryBus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@Profile("demo-integrated")
@RequestMapping("/api/v2/me/orders")
class MyOrdersV2Controller {

    private static final String ORDERS = "/api/v2/me/orders";

    private final CommandBus commands;
    private final QueryBus queries;

    MyOrdersV2Controller(CommandBus commands, QueryBus queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @GetMapping
    PageResponse list(@AuthenticationPrincipal Jwt principal,
                      @RequestParam(defaultValue = "1") @Min(1) @Max(100) int page,
                      @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size,
                      @RequestParam(required = false) @Pattern(regexp = "lines") String include) {
        var result = queries.dispatch(new MyOrders.ListPage(CustomerId.from(principal.getSubject()), page, size, include != null));
        var orders = result.orders().stream().map(order -> new OrderSummaryResponseV2(
                order.orderId(), order.status(), order.totalMinor(), order.currency(), order.createdAt(),
                order.lines() == null ? null : order.lines().stream().map(OrderLineResponse::from).toList())).toList();
        return new PageResponse(orders, result.page(), result.hasNext());
    }

    @GetMapping("/{orderId}")
    DetailResponse detail(@AuthenticationPrincipal Jwt principal, @PathVariable UUID orderId) {
        var result = queries.dispatch(new MyOrders.Detail(CustomerId.from(principal.getSubject()), OrderId.of(orderId)));
        return new DetailResponse(result.orderId(), result.status(), result.totalMinor(), result.currency(), result.createdAt(), result.version(),
                result.lines().stream().map(OrderLineResponse::from).toList());
    }

    @PutMapping("/{orderId}/cancellation")
    ResponseEntity<Cancellation> createCancellation(@AuthenticationPrincipal Jwt principal, @PathVariable UUID orderId,
                                                     @Valid @RequestBody CancelRequest request) {
        var result = commands.dispatch(new CancelOwnedOrderCommand(CustomerId.from(principal.getSubject()), OrderId.of(orderId),
                request.reason(), request.expectedVersion()));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .header(HttpHeaders.LOCATION, ORDERS + "/" + orderId + "/cancellation")
                .body(result.cancellation());
    }

    @GetMapping("/{orderId}/cancellation")
    Cancellation getCancellation(@AuthenticationPrincipal Jwt principal, @PathVariable UUID orderId) {
        return queries.dispatch(new MyOrders.GetCancellation(CustomerId.from(principal.getSubject()), OrderId.of(orderId)));
    }

    @PutMapping("/{orderId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@AuthenticationPrincipal Jwt principal, @PathVariable UUID orderId, @Valid @RequestBody CancelRequest request) {
        commands.dispatch(new CancelOwnedOrderCommand(CustomerId.from(principal.getSubject()), OrderId.of(orderId),
                request.reason(), request.expectedVersion()));
    }

    record CancelRequest(@NotBlank String reason, @NotNull @Min(0) @Max(Checkout.MAX_MINOR) Long expectedVersion) {}
    record PageResponse(List<OrderSummaryResponseV2> orders, int page, boolean hasNext) {}
    record DetailResponse(UUID orderId, String status, long totalMinor, String currency, Instant createdAt, long version,
                          List<OrderLineResponse> lines) {}
}
