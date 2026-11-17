package com.example.order.application.checkout;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("demo.checkout")
public record CheckoutIntakeProperties(@DefaultValue("true") boolean intakeEnabled) {}
