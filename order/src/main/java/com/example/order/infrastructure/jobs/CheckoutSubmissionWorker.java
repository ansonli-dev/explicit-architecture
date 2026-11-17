package com.example.order.infrastructure.jobs;

import com.example.order.application.checkout.ResolveCheckoutSubmission;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("demo-integrated")
@ConditionalOnProperty(name = "demo.checkout.worker-enabled", havingValue = "true", matchIfMissing = true)
class CheckoutSubmissionWorker {
    private final ResolveCheckoutSubmission resolver;

    CheckoutSubmissionWorker(ResolveCheckoutSubmission resolver) {
        this.resolver = resolver;
    }

    @Scheduled(fixedDelay = 500)
    void poll() {
        resolver.processNext();
    }
}
