package com.example.catalog.infrastructure.repository.jpa;

import com.example.catalog.application.port.outbound.CatalogWriteTransaction;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

@Component
class CatalogTransactionAdapter implements CatalogWriteTransaction {

    @Override
    @Transactional
    public <T> T run(Supplier<T> operation) {
        return operation.get();
    }
}
