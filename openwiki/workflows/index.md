# Files

- [Architecture evidence and specification workflow](architecture-evidence.md) - Maps the repository's architecture skills, authority and baseline distinctions, specification templates, and structural validator to their intended tasks.
- [Catalog queries and stock mutation](catalog-inventory.md) - Explains Catalog's direct use-case entrypoints, stock-count model, persistence and cache lifecycle, and cancellation-driven release semantics.
- [Event-driven notification records](notifications.md) - Traces order events into notification commands, recipient lookup and delivery records, with the actual demo adapters and Kafka transaction context.
- [Order placement, compensation, and lifecycle](order-lifecycle.md) - Traces order commands through pricing, synchronous inventory reservation, persistence, local auto-confirmation, and asynchronous cancellation effects.
