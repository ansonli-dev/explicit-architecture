-- Legacy retries store raw Avro with no writer schema ID. The new final field is Avro int 1 (zigzag 0x02).
UPDATE consumer_retry_events
SET avro_payload = avro_payload || decode('02', 'hex')
WHERE avro_class_name = 'com.example.events.v1.OrderCancelled';
