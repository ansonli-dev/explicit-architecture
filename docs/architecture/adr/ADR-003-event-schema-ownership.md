# ADR-003: shared-events as an Avro Schema SDK

- **Status**: Accepted
- **Date**: 2026-03-04
- **Deciders**: Architecture Team

---

## Context

Microservices communicate asynchronously via Kafka; every message has a producer and one or more consumers. We need to decide:

1. **Schema format**: what describes the event structure?
2. **Schema ownership**: who owns and maintains it?
3. **Code generation**: how do consumers/producers get strongly-typed message classes?
4. **Compatibility guarantees**: how do we prevent breaking schema changes from causing runtime failures?

### Options Considered

| Option | Description | Problem |
|---|---|---|
| A — Each service defines its own DTOs | Producers and consumers each write their own classes | Schema drift; mismatches are undetectable at compile time |
| B — Shared Java Records | `shared-events` contains Java records | No serialization standard; no Schema Registry compatibility checks |
| C — Avro + Schema Registry | `.avsc` files define the schema, code is generated, Schema Registry enforces compatibility | Introduces Avro and Schema Registry dependencies, but this is the industry standard |
| D — Protobuf | Similar to C, different serialization format | Integration with the Confluent ecosystem (Schema Registry, Debezium) is less mature than Avro |

---

## Decision

Adopt **Option C**: use `shared-events` as a **centralized Avro Schema SDK**.

### Responsibilities of shared-events

```
.avsc files (source)
    ↓ Avro Gradle Plugin (at build time)
Java SpecificRecord classes (artifact)
    ↓ publishToMavenLocal
Microservice dependencies (consumers)
```

The `shared-events` module:
- Is the **single source of truth** for `.avsc` schema files
- Generates Java classes at **build time** via the Avro Gradle Plugin (`com.github.davidmc24.gradle.plugin.avro`)
- Generated classes are subclasses of `org.apache.avro.specific.SpecificRecord`, carrying full type information
- Is published as a Gradle/Maven library (published to `mavenLocal()` in the demo); services declare an `implementation` dependency
- **Contains no business logic, Spring beans, or domain objects**

### Which events belong in shared-events

Only events **consumed cross-service**. Events internal to a single service (e.g., read-model projection triggers inside `order`) are not included here.

| Event | Producer | Consumer |
|---|---|---|
| `OrderPlaced` | order | notification |
| `OrderConfirmed` | order | notification |
| `OrderCancelled` | order | notification, catalog |
| `OrderShipped` | order | notification |
| `StockReserved` | catalog | order |
| `StockReleased` | catalog | — |

### Role of Schema Registry

Schema Registry (Confluent) acts as the **compatibility gatekeeper** at runtime:

- When a producer first sends a message, `KafkaAvroSerializer` automatically registers the schema with Schema Registry and receives a `schema_id`
- Message format: `[magic byte(1)] + [schema_id(4)] + [avro binary payload]`
- When a consumer receives a message, it fetches the schema from Schema Registry by `schema_id` (locally cached) and deserializes
- Schema Registry **enforces compatibility** when registering a new version (default: `BACKWARD` mode), blocking breaking changes

### Debezium and Avro Integration

Debezium Connect uses `AvroConverter` to publish Outbox messages to Kafka in Avro format; schemas are automatically registered with Schema Registry:

```json
"key.converter": "io.confluent.connect.avro.AvroConverter",
"key.converter.schema.registry.url": "http://schema-registry:8081",
"value.converter": "io.confluent.connect.avro.AvroConverter",
"value.converter.schema.registry.url": "http://schema-registry:8081"
```

### Mapping Domain Events to Avro Messages

The domain layer of each microservice continues to use **pure Java records** as domain events. Avro classes appear only in the `infrastructure/messaging/` adapter layer:

```
domain event (pure Java record)         ← domain layer, no Avro dependency
    ↓ mapped in infrastructure/messaging/ adapter
Avro SpecificRecord (shared-events)     ← infrastructure layer, Kafka serialization
```

This preserves zero dependencies on Avro and Kafka in the domain layer (ADR-001 architectural rule).

---

## Consequences

### Positive

- **Compile-time contract**: producers and consumers depend on the same generated class; field mismatches are caught at compile time
- **Schema evolution with tooling**: Schema Registry blocks incompatible changes in CI or at deployment
- **Efficient serialization**: Avro binary format is 50–80% smaller than JSON, meaningful for high-throughput scenarios
- **Self-documenting**: the `doc` field in `.avsc` files serves as living documentation; consuming teams can read it directly
- **Native Debezium support**: Debezium Outbox Event Router integrates maturely with Avro + Schema Registry

### Negative

- **Build dependency**: since each service is an independent Gradle project, `./gradlew publishToMavenLocal` must be run in `shared-events/` before any service can use the latest SDK
- **Avro learning curve**: developers need to understand Avro schema syntax and evolution rules
- **Generated code style**: Avro-generated Java classes (Builder pattern, not records) are inconsistent with the rest of the project (records), but they only appear in the infrastructure layer

### How Services Reference the SDK (Independent Modules)

Each service is an independent Gradle project (not a multi-project build); all reference the shared-events SDK via `mavenLocal()`:

```bash
# After modifying schemas, run from the shared-events/ directory
./gradlew publishToMavenLocal
```

```kotlin
// Each service's build.gradle.kts
repositories {
    mavenLocal()   // prefer locally published shared-events
    maven { url = uri("https://packages.confluent.io/maven/") }
    mavenCentral()
}

dependencies {
    implementation("com.example:shared-events:0.1.0")  // update with version
}
```

### Evolution Path

In production, `shared-events` can be published to a private Maven repository (Nexus, GitHub Packages, Artifactory); services switch to coordinate references, while the publish-and-consume logic remains unchanged. The demo uses `mavenLocal()`.

---

## SDK Formalization (Demo Phase Addendum)

Building on the base schema → code-generation capability, `shared-events` establishes three tiers of responsibility:

| Tier | Contents |
|---|---|
| **Schema tier** | `.avsc` source files (single source of truth) + Schema Registry local pre-registration script |
| **SDK tier** | Avro SpecificRecord generated classes + `mavenLocal()` publication |
| **Documentation tier** | Event catalog, evolution rules, `CHANGELOG.md` |

**Schema Registry pre-registration** (`schema-registry/register-schemas.sh`) allows developers to validate schema compatibility and complete registration immediately after Schema Registry starts, without waiting until service runtime to discover serialization errors:

```bash
./schema-registry/register-schemas.sh              # register all schemas
./schema-registry/register-schemas.sh --check-only # check compatibility only
```

Versioning follows the conventions in [ADR-008](ADR-008-shared-events-versioning.md).
