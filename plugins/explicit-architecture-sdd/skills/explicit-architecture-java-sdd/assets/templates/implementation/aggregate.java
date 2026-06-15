package {{BASE_PACKAGE}}.domain.model;

import {{BASE_PACKAGE}}.domain.event.{{AGGREGATE}}{{PAST_TENSE_EVENT}};
import {{DOMAIN_EVENT_IMPORT}};

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class {{AGGREGATE}} {
    private final {{AGGREGATE}}Id id;
    private {{AGGREGATE}}Status status;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private {{AGGREGATE}}({{AGGREGATE}}Id id, {{AGGREGATE}}Status status) {
        this.id = id;
        this.status = status;
    }

    public static {{AGGREGATE}} create({{AGGREGATE}}Id id) {
        var aggregate = new {{AGGREGATE}}(id, {{AGGREGATE}}Status.CREATED);
        aggregate.registerEvent(new {{AGGREGATE}}{{PAST_TENSE_EVENT}}(UUID.randomUUID(), id.value(), Instant.now()));
        return aggregate;
    }

    public static {{AGGREGATE}} reconstitute({{AGGREGATE}}Id id, {{AGGREGATE}}Status status) {
        return new {{AGGREGATE}}(id, status);
    }

    public void {{BUSINESS_METHOD}}({{ACTOR_ID_TYPE}} actorId) {
        if (status != {{AGGREGATE}}Status.CREATED) {
            throw new {{AGGREGATE}}StateException("{{BUSINESS_RULE_MESSAGE}}");
        }
        status = {{AGGREGATE}}Status.{{NEXT_STATUS}};
        registerEvent(new {{AGGREGATE}}{{PAST_TENSE_EVENT}}(UUID.randomUUID(), id.value(), Instant.now()));
    }

    public {{AGGREGATE}}Id id() {
        return id;
    }

    public {{AGGREGATE}}Status status() {
        return status;
    }

    public List<DomainEvent> pullDomainEvents() {
        var events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }
}
