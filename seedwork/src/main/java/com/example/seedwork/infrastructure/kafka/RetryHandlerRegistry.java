package com.example.seedwork.infrastructure.kafka;

import org.apache.avro.specific.SpecificRecord;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Collects all {@link RetryableKafkaHandler} beans and provides dispatch by Avro class name.
 * Autopopulated by Spring via constructor injection of the handler list.
 *
 * <p>This replaces the manual {@code registerRetryHandler} calls — handlers register
 * themselves simply by being Spring beans.
 */
public class RetryHandlerRegistry {

    private final Map<String, RetryableKafkaHandler<SpecificRecord>> byClassName;

    @SuppressWarnings("unchecked")
    public RetryHandlerRegistry(List<RetryableKafkaHandler<?>> handlers) {
        byClassName = handlers.stream()
                .collect(Collectors.toMap(
                        h -> h.eventType().getName(),
                        h -> (RetryableKafkaHandler<SpecificRecord>) h));
    }

    /**
     * Dispatch a deserialized Avro record to the registered handler.
     *
     * @throws IllegalStateException if no handler is registered for the given class name
     */
    public void dispatch(String avroClassName, SpecificRecord record) {
        RetryableKafkaHandler<SpecificRecord> handler = byClassName.get(avroClassName);
        if (handler == null) {
            throw new IllegalStateException("No RetryableKafkaHandler registered for: " + avroClassName);
        }
        handler.handle(record);
    }

    public boolean hasHandler(String avroClassName) {
        return byClassName.containsKey(avroClassName);
    }
}
