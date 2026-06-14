package {{BASE_PACKAGE}}.domain.ports;

import {{BASE_PACKAGE}}.domain.model.{{AGGREGATE}};
import {{BASE_PACKAGE}}.domain.model.{{AGGREGATE}}Id;

public interface {{AGGREGATE}}Repository {
    {{AGGREGATE}} getById({{AGGREGATE}}Id id);

    void save({{AGGREGATE}} aggregate);
}
