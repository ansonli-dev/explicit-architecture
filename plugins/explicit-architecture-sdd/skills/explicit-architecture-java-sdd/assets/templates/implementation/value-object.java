package {{BASE_PACKAGE}}.domain.model;

import java.util.UUID;

public record {{VALUE_OBJECT}}({{VALUE_TYPE}} value) {
    public {{VALUE_OBJECT}} {
        if (value == null) {
            throw new IllegalArgumentException("{{VALUE_OBJECT}} must not be null");
        }
    }

    public static {{VALUE_OBJECT}} newId() {
        return new {{VALUE_OBJECT}}({{NEW_VALUE_EXPRESSION}});
    }
}
