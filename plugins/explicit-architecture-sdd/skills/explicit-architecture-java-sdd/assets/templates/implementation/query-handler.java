package {{BASE_PACKAGE}}.application.query.{{FEATURE_PACKAGE}};

import {{BASE_PACKAGE}}.application.port.outbound.{{AGGREGATE}}ReadRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

public record Get{{AGGREGATE}}Query(UUID aggregateId) {}

public record {{AGGREGATE}}DetailResult(
    UUID aggregateId,
    String status,
    Instant createdAt
) {}

@Service
public class Get{{AGGREGATE}}QueryHandler {
    private final {{AGGREGATE}}ReadRepository readRepository;

    public Get{{AGGREGATE}}QueryHandler({{AGGREGATE}}ReadRepository readRepository) {
        this.readRepository = readRepository;
    }

    public {{AGGREGATE}}DetailResult handle(Get{{AGGREGATE}}Query query) {
        return readRepository.findDetailById(query.aggregateId())
            .orElseThrow(() -> new {{AGGREGATE}}NotFoundException(query.aggregateId()));
    }
}
