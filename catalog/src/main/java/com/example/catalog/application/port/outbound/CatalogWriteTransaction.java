package com.example.catalog.application.port.outbound;

import java.util.function.Supplier;

/** Runs a local Catalog operation with one transaction and no cross-service calls. */
public interface CatalogWriteTransaction {
    <T> T run(Supplier<T> operation);
}
