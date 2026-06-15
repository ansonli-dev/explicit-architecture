package {{BASE_PACKAGE}}.infrastructure.messaging.outbox;

import {{BASE_PACKAGE}}.domain.event.{{DOMAIN_EVENT_NAME}};
import {{DOMAIN_EVENT_IMPORT}};
import {{OUTBOX_MAPPER_IMPORT}};
import org.springframework.stereotype.Component;

@Component
public class {{SERVICE}}OutboxMapper implements OutboxMapper {
    @Override
    public boolean supports(DomainEvent event) {
        return event instanceof {{DOMAIN_EVENT_NAME}};
    }

    @Override
    public Object toMessage(DomainEvent event) {
        if (event instanceof {{DOMAIN_EVENT_NAME}} domainEvent) {
            return {{INTEGRATION_EVENT_FACTORY_EXPRESSION}};
        }
        throw new IllegalArgumentException("Unsupported domain event: " + event.getClass().getName());
    }
}
