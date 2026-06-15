package {{BASE_PACKAGE}}.interfaces.rest.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record {{ACTION}}{{AGGREGATE}}Request(
    @NotNull UUID actorId
) {}
