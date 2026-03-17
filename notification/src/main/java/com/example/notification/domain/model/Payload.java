package com.example.notification.domain.model;

public record Payload(String subject, String body) {
    public Payload {
        if (subject == null || subject.isBlank())
            throw new IllegalArgumentException("Subject must not be blank");
        if (body == null || body.isBlank())
            throw new IllegalArgumentException("Body must not be blank");
    }
}
