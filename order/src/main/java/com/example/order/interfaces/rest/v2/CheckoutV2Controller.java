package com.example.order.interfaces.rest.v2;

import com.example.order.application.checkout.Checkout;
import com.example.order.application.checkout.CheckoutMessages;
import com.example.order.application.checkout.CheckoutProblem;
import com.example.order.domain.model.CustomerId;
import com.example.seedwork.application.bus.CommandBus;
import com.example.seedwork.application.bus.QueryBus;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@Profile("demo-integrated")
@RequestMapping("/api/v2/me")
class CheckoutV2Controller {

    private static final String QUOTES = "/api/v2/me/order-quotes";
    private static final String SUBMISSIONS = "/api/v2/me/order-submissions";

    private final CommandBus commands;
    private final QueryBus queries;

    CheckoutV2Controller(CommandBus commands, QueryBus queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping("/order-quotes")
    ResponseEntity<QuoteResponse> quote(@AuthenticationPrincipal Jwt principal, @Valid @RequestBody QuoteRequest request) {
        var lines = request.lines().stream()
                .map(line -> new Checkout.RequestedLine(line.bookId(), line.quantity())).toList();
        var result = commands.dispatch(new CheckoutMessages.QuoteOrder(
                CustomerId.from(principal.getSubject()), verifiedEmail(principal), lines));
        return ResponseEntity.created(URI.create(QUOTES + "/" + result.quoteId())).body(QuoteResponse.from(result));
    }

    @GetMapping("/order-quotes/{quoteId}")
    QuoteResponse readQuote(@AuthenticationPrincipal Jwt principal, @PathVariable UUID quoteId) {
        return QuoteResponse.from(queries.dispatch(new CheckoutMessages.GetQuote(
                CustomerId.from(principal.getSubject()), quoteId)));
    }

    @PostMapping("/order-submissions")
    ResponseEntity<SubmissionResponse> submit(@AuthenticationPrincipal Jwt principal, @RequestHeader("Idempotency-Key") UUID key,
                                                @Valid @RequestBody SubmitRequest request) {
        var result = commands.dispatch(new CheckoutMessages.SubmitOrder(
                CustomerId.from(principal.getSubject()), request.quoteId(), key));
        var response = switch (result.state()) {
            case PLACED, REJECTED -> ResponseEntity.ok();
            case PROCESSING, RECONCILE_REQUIRED -> ResponseEntity.accepted();
        };
        return response.header(HttpHeaders.LOCATION, SUBMISSIONS + "/" + result.submissionId())
                .body(SubmissionResponse.from(result));
    }

    @GetMapping("/order-submissions/{submissionId}")
    SubmissionResponse submission(@AuthenticationPrincipal Jwt principal, @PathVariable UUID submissionId) {
        return SubmissionResponse.from(queries.dispatch(new CheckoutMessages.GetSubmission(
                CustomerId.from(principal.getSubject()), submissionId)));
    }

    private static String verifiedEmail(Jwt principal) {
        if (Boolean.TRUE.equals(principal.getClaims().get("email_verified"))
                && principal.getClaims().get("email") instanceof String email && !email.isBlank() && email.length() <= 320) {
            return email;
        }
        throw new CheckoutProblem(CheckoutProblem.Code.EMAIL_UNVERIFIED);
    }

    record LineRequest(@NotNull UUID bookId, @NotNull @Min(1) @Max(99) Integer quantity) {}
    record QuoteRequest(@NotNull @Size(min = 1, max = 20) List<@NotNull @Valid LineRequest> lines) {}
    record SubmitRequest(@NotNull UUID quoteId) {}
    record QuoteResponse(UUID quoteId, Instant expiresAt, List<OrderLineResponse> lines, long totalMinor, String currency) {
        static QuoteResponse from(Checkout.Quote quote) {
            return new QuoteResponse(quote.quoteId(), quote.expiresAt(),
                    quote.lines().stream().map(OrderLineResponse::from).toList(), quote.totalMinor(), quote.currency());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SubmissionResponse(UUID submissionId, Checkout.State state, OrderSummaryResponseV2 order, Checkout.Rejection reason) {
        static SubmissionResponse from(Checkout.Submission submission) {
            var placed = submission.order();
            var order = placed == null ? null
                    : new OrderSummaryResponseV2(placed.orderId(), placed.status(), placed.totalMinor(), placed.currency(),
                            placed.createdAt(), null);
            return new SubmissionResponse(submission.submissionId(), submission.state(), order, submission.reason());
        }
    }
}
