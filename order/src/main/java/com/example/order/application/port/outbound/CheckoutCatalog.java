package com.example.order.application.port.outbound;

import com.example.order.application.checkout.Checkout;

import java.util.List;
import java.util.UUID;

public interface CheckoutCatalog {
    List<Checkout.Snapshot> snapshot(List<Checkout.RequestedLine> lines);
    Checkout.Reservation reserve(UUID submissionId, List<Checkout.Line> lines);
    void release(UUID submissionId);
}
