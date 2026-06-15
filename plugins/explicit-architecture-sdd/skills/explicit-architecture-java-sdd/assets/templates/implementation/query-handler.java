package {{BASE_PACKAGE}}.application.query.{{FEATURE_PACKAGE}};

import {{BASE_PACKAGE}}.application.port.outbound.{{AGGREGATE}}ReadRepository;
import {{QUERY_HANDLER_IMPORT}};
import org.springframework.stereotype.Service;

@Service
public class Get{{AGGREGATE}}QueryHandler implements QueryHandler<Get{{AGGREGATE}}Query, {{AGGREGATE}}DetailResult> {
    private final {{AGGREGATE}}ReadRepository readRepository;

    public Get{{AGGREGATE}}QueryHandler({{AGGREGATE}}ReadRepository readRepository) {
        this.readRepository = readRepository;
    }

    @Override
    public {{AGGREGATE}}DetailResult handle(Get{{AGGREGATE}}Query query) {
        return readRepository.findDetailById(query.aggregateId())
            .orElseThrow(() -> new {{AGGREGATE}}NotFoundException(query.aggregateId()));
    }
}
