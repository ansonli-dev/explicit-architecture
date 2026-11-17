package com.example.events;

import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientFactory;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import org.apache.avro.Schema;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class AvroOutboxSerializer {

    private AvroOutboxSerializer() {}

    /** Resolves exact topic schema IDs before any transaction using the serializer starts. */
    public static KafkaAvroSerializer preload(Map<String, ?> properties, Map<String, Schema> schemasByTopic) {
        var config = new KafkaAvroSerializerConfig(properties);
        var registry = SchemaRegistryClientFactory.newClient(config.getSchemaRegistryUrls(), config.getMaxSchemasPerSubject(),
                List.of(new AvroSchemaProvider()), properties, config.requestHeaders());
        try {
            for (var entry : schemasByTopic.entrySet()) {
                String subject = entry.getKey() + "-value";
                var schema = new AvroSchema(entry.getValue());
                if (config.autoRegisterSchema()) {
                    registry.register(subject, schema, config.normalizeSchema());
                } else {
                    registry.getId(subject, schema, config.normalizeSchema());
                }
            }
            return new KafkaAvroSerializer(registry);
        } catch (IOException | RestClientException e) {
            throw new IllegalStateException("Could not load event schemas from Schema Registry", e);
        }
    }
}
