package {{BASE_PACKAGE}}.application.command.{{FEATURE_PACKAGE}};

import {{BASE_PACKAGE}}.domain.model.{{AGGREGATE}};
import {{BASE_PACKAGE}}.domain.model.{{AGGREGATE}}Id;
import {{BASE_PACKAGE}}.domain.model.{{AGGREGATE}}Status;
import {{BASE_PACKAGE}}.domain.ports.{{AGGREGATE}}Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class {{ACTION}}{{AGGREGATE}}CommandHandlerTest {
    @Mock
    {{AGGREGATE}}Repository repository;

    @Test
    void handles_command_and_saves_aggregate() {
        var aggregateId = UUID.randomUUID();
        var actorId = UUID.randomUUID();
        var aggregate = {{AGGREGATE}}.create(new {{AGGREGATE}}Id(aggregateId));

        when(repository.getById(new {{AGGREGATE}}Id(aggregateId))).thenReturn(aggregate);

        var handler = new {{ACTION}}{{AGGREGATE}}CommandHandler(repository);
        var result = handler.handle(new {{ACTION}}{{AGGREGATE}}Command(aggregateId, actorId));

        var saved = ArgumentCaptor.forClass({{AGGREGATE}}.class);
        verify(repository).save(saved.capture());

        assertThat(saved.getValue().status()).isEqualTo({{AGGREGATE}}Status.{{NEXT_STATUS}});
        assertThat(result.aggregateId()).isEqualTo(aggregateId);
        assertThat(result.status()).isEqualTo("{{NEXT_STATUS}}");
    }
}
