package {{BASE_PACKAGE}}.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class {{AGGREGATE}}Test {
    @Test
    void {{BUSINESS_METHOD}}_changes_state_and_records_event() {
        var aggregate = {{AGGREGATE}}.create(new {{AGGREGATE}}Id(UUID.randomUUID()));

        aggregate.{{BUSINESS_METHOD}}(new {{ACTOR_ID_TYPE}}(UUID.randomUUID()));

        assertThat(aggregate.status()).isEqualTo({{AGGREGATE}}Status.{{NEXT_STATUS}});
        assertThat(aggregate.pullDomainEvents())
            .anyMatch({{AGGREGATE}}{{PAST_TENSE_EVENT}}.class::isInstance);
    }

    @Test
    void {{BUSINESS_METHOD}}_rejects_invalid_state() {
        var aggregate = {{AGGREGATE}}.reconstitute(
            new {{AGGREGATE}}Id(UUID.randomUUID()),
            {{AGGREGATE}}Status.{{INVALID_STATUS}}
        );

        assertThatThrownBy(() -> aggregate.{{BUSINESS_METHOD}}(new {{ACTOR_ID_TYPE}}(UUID.randomUUID())))
            .isInstanceOf({{AGGREGATE}}StateException.class)
            .hasMessage("{{BUSINESS_RULE_MESSAGE}}");
    }
}
