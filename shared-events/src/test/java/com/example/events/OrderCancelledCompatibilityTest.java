package com.example.events;

import com.example.events.v1.OrderCancelled;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderCancelledCompatibilityTest {

    @Test
    void aLegacyWriterDefaultsToReservationProtocolOneForTheCurrentReader() throws IOException {
        final Schema writerSchema;
        try (var resource = getClass().getResourceAsStream("/avro/OrderCancelled-0.1.0.avsc")) {
            writerSchema = new Schema.Parser().parse(resource);
        }
        var itemSchema = writerSchema.getField("items").schema().getElementType();
        var item = new GenericData.Record(itemSchema);
        item.put("bookId", "30000000-0000-0000-0000-000000000001");
        item.put("bookTitle", "Legacy book");
        item.put("quantity", 3);
        item.put("unitPriceCents", 1050L);

        var written = new GenericData.Record(writerSchema);
        written.put("eventId", "20000000-0000-0000-0000-000000000001");
        written.put("orderId", "10000000-0000-0000-0000-000000000001");
        written.put("customerId", "40000000-0000-0000-0000-000000000001");
        written.put("reason", "Cancelled");
        written.put("items", List.of(item));
        written.put("occurredAt", 42L);
        var bytes = new ByteArrayOutputStream();
        var encoder = EncoderFactory.get().binaryEncoder(bytes, null);
        new GenericDatumWriter<GenericData.Record>(writerSchema).write(written, encoder);
        encoder.flush();

        var reader = new SpecificDatumReader<OrderCancelled>(writerSchema, OrderCancelled.getClassSchema());
        var decoded = reader.read(null, DecoderFactory.get().binaryDecoder(bytes.toByteArray(), null));

        assertEquals(1, decoded.getReservationProtocol());
        assertEquals("10000000-0000-0000-0000-000000000001", decoded.getOrderId());
        assertEquals(3, decoded.getItems().getFirst().getQuantity());
        assertEquals(Instant.ofEpochMilli(42), decoded.getOccurredAt());
    }
}
