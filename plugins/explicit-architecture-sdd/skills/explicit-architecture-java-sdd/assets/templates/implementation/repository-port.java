package {{BASE_PACKAGE}}.domain.ports;

import {{BASE_PACKAGE}}.domain.model.{{AGGREGATE}};
import {{BASE_PACKAGE}}.domain.model.{{AGGREGATE}}Id;

public interface {{AGGREGATE}}Persistence {
    {{AGGREGATE}} getById({{AGGREGATE}}Id id);

    void save({{AGGREGATE}} aggregate);
}
