package com.example.notification.domain.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayloadTest {

    @Nested
    class SubjectValidation {

        @Test
        void givenNullSubject_whenConstruct_thenThrowsIllegalArgument() {
            assertThatThrownBy(() -> new Payload(null, "Some body"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Subject");
        }

        @Test
        void givenBlankSubject_whenConstruct_thenThrowsIllegalArgument() {
            assertThatThrownBy(() -> new Payload("  ", "Some body"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Subject");
        }
    }

    @Nested
    class BodyValidation {

        @Test
        void givenNullBody_whenConstruct_thenThrowsIllegalArgument() {
            assertThatThrownBy(() -> new Payload("Subject", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Body");
        }

        @Test
        void givenBlankBody_whenConstruct_thenThrowsIllegalArgument() {
            assertThatThrownBy(() -> new Payload("Subject", "   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Body");
        }
    }

    @Nested
    class ValidPayload {

        @Test
        void givenValidSubjectAndBody_whenConstruct_thenCreatedSuccessfully() {
            // Act
            Payload payload = new Payload("Order Placed", "Your order has been placed.");

            // Assert
            assertThat(payload.subject()).isEqualTo("Order Placed");
            assertThat(payload.body()).isEqualTo("Your order has been placed.");
        }
    }
}
