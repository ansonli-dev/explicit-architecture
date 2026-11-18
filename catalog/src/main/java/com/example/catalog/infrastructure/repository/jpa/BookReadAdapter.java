package com.example.catalog.infrastructure.repository.jpa;

import com.example.catalog.application.port.outbound.BookReadRepository;
import com.example.catalog.application.query.book.BookV2Result;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class BookReadAdapter implements BookReadRepository {

    private static final String BOOK = """
            select new com.example.catalog.application.query.book.BookV2Result(
                b.id, b.title, b.authorName, c.name, b.priceCents, b.currency, b.stockTotal - b.stockReserved, b.version,
                b.coverUrl, b.description)
            from BookJpaEntity b join b.category c
            """;

    private final EntityManager entityManager;

    @Override
    public List<BookV2Result> search(String category, String query, int offset, int limit) {
        String pattern = query == null ? "%" : "%" + query.replace("!", "!!")
                .replace("%", "!%").replace("_", "!_") + "%";
        return entityManager.createQuery(BOOK + """
                where (:category is null or c.name = :category)
                    and (lower(b.title) like lower(:pattern) escape '!' or
                         lower(b.authorName) like lower(:pattern) escape '!')
                order by b.id
                """, BookV2Result.class)
                .setParameter("category", category).setParameter("pattern", pattern)
                .setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    @Override
    public Optional<BookV2Result> find(UUID id) {
        return entityManager.createQuery(BOOK + "where b.id = :id", BookV2Result.class)
                .setParameter("id", id).getResultList().stream().findFirst();
    }

    @Override
    public List<BookV2Result> findAll(Collection<UUID> ids) {
        return entityManager.createQuery(BOOK + "where b.id in :ids order by b.id", BookV2Result.class)
                .setParameter("ids", ids).getResultList();
    }

    @Override
    public List<String> categories() {
        return entityManager.createQuery("select c.name from CategoryJpaEntity c order by c.name", String.class)
                .getResultList();
    }
}
