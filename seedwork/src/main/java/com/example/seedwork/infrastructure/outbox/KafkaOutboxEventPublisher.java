package com.example.seedwork.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.kafka.core.KafkaTemplate;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * Reconstructs the Avro record from stored binary bytes via reflection and sends it
 * to the topic and key stored in the outbox row. No service-specific code needed.
 *
 * <p>Uses the generated static {@code fromByteBuffer(ByteBuffer)} method present on
 * every Avro {@link SpecificRecord} class to deserialize without manual switch/case.
 */
@RequiredArgsConstructor
public class KafkaOutboxEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(OutboxEventJpaEntity entry) throws Exception {
        SpecificRecord record = deserialize(entry.getAvroClassName(), entry.getAvroPayload());
        kafkaTemplate.send(entry.getTopic(), entry.getMessageKey(), record).get();
    }

    private static SpecificRecord deserialize(String avroClassName, byte[] bytes) throws Exception {
        Class<?> clazz = Class.forName(avroClassName);
        Method fromByteBuffer = clazz.getMethod("fromByteBuffer", ByteBuffer.class);
        return (SpecificRecord) fromByteBuffer.invoke(null, ByteBuffer.wrap(bytes));
    }
}
