package {{BASE_PACKAGE}}.application.query.{{FEATURE_PACKAGE}};

import {{BASE_PACKAGE}}.application.port.outbound.{{AGGREGATE}}ReadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Get{{AGGREGATE}}QueryHandlerTest {
    @Mock
    {{AGGREGATE}}ReadRepository readRepository;

    @Test
    void returns_detail_result_from_read_repository() {
        var aggregateId = UUID.randomUUID();
        var expected = new {{AGGREGATE}}DetailResult(aggregateId, "{{STATUS}}", Instant.parse("2026-01-01T00:00:00Z"));
        when(readRepository.findDetailById(aggregateId)).thenReturn(Optional.of(expected));

        var handler = new Get{{AGGREGATE}}QueryHandler(readRepository);

        assertThat(handler.handle(new Get{{AGGREGATE}}Query(aggregateId))).isEqualTo(expected);
    }
}
