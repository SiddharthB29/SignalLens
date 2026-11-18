# SignalLens Architecture

**Status:** Describes the current backend implementation (November 2026).

## Overview

SignalLens currently consists of two Spring Boot services connected through Kafka. The ingestion service accepts and validates events, then publishes them asynchronously. The consumer processes each message, persists it and its failure grouping in PostgreSQL, and serves APIs for browsing and managing failure groups.

```text
Producer
  │ POST /api/v1/events
  ▼
Event Ingestion (port 8080) ── Kafka: signallens.events ──► Event Consumer (port 8081)
                                                              │
                                                              ▼
                                                        PostgreSQL
                                                              ▲
Dashboard / API client ── GET/PATCH /api/failure-groups ──────┘
```

## Components

### Event ingestion service

`services/event-ingestion` exposes `POST /api/v1/events`. It deserializes and validates the request, normalizes selected strings, applies the 64 KiB request-body limit, and publishes an `IngestedEvent` to Kafka using `eventId` as the message key. The asynchronous HTTP response is `202 Accepted` after the Kafka send future succeeds. A publish failure returns `503 Service Unavailable`. Validation or malformed input returns `400`; oversized input returns `413`.

The service currently has no authentication, durable idempotency store, duplicate payload comparison, or rate limiting. The Kafka key is useful for partitioning but does not itself deduplicate submissions.

### Kafka

The local Compose setup runs a single Apache Kafka broker on port `9092`. It is configured with three partitions and a persistent Docker volume. Both services use the topic `signallens.events`. The consumer group ID is `signallens-event-consumer`.

### Event consumer and persistence

`services/event-consumer` consumes Kafka messages, classifies severity as `Warning`, `Application Error`, or `Critical Failure`, and persists processed event records in PostgreSQL. Event IDs are primary deduplication keys: an existing event is skipped, and an atomic insert protects against concurrent duplicate deliveries. Event persistence and failure grouping run transactionally.

Failure groups are identified by a SHA-256 fingerprint over service name, environment, exception type, and normalized message. Message normalization strips outer whitespace, lowercases the text, and collapses whitespace. Each distinct event occurrence is stored against its group; repeated event IDs do not increment the group count. A new group starts as `OPEN`. Group status changes are `OPEN → ACKNOWLEDGED`, `OPEN/ACKNOWLEDGED → RESOLVED`, and `RESOLVED → OPEN`.

The local database is PostgreSQL on port `5432`, database name configured by Compose. The consumer connects using the `signallens_db_pswrd` environment variable. Hibernate currently uses `ddl-auto=update` and SQL logging; use a managed schema migration strategy and production-safe settings before deployment.

### Failure management API

The consumer serves APIs on port `8081`:

- `GET /api/failure-groups` lists groups, with optional `status`, `serviceName`, and `environment` filters and pagination.
- `GET /api/failure-groups/{id}` returns one group.
- `GET /api/failure-groups/{id}/occurrences` lists its occurrences.
- `PATCH /api/failure-groups/{id}/acknowledge`, `/resolve`, and `/reopen` perform the supported status transitions.

Pages default to 20 items and are capped at 100. Sorting is restricted to allowlisted fields. Missing resources return 404; invalid query values return 400; invalid state transitions return 409. Authentication and authorization are not implemented.

## Event lifecycle

1. A producer submits one event to the ingestion endpoint.
2. The ingestion service validates the request and waits for Kafka's send result before acknowledging acceptance.
3. Kafka delivers the event to the consumer group.
4. The consumer stores the processed event and creates or updates a failure group and occurrence in a database transaction.
5. A client queries or changes failure-group status through the consumer API.

HTTP acceptance means Kafka acknowledged the publish; it does not mean PostgreSQL processing is complete. The current flow does not use an outbox or Kafka/database distributed transaction. Production delivery, retry, dead-letter, and offset-commit guarantees require further configuration and validation.

## Local infrastructure

`infrastructure/docker/docker-compose.yml` defines Kafka and PostgreSQL with named data volumes. Compose reads `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` from the environment or a root `.env` file. Both Spring services currently point to localhost endpoints, so they are expected to run on the host while these dependencies run in Docker.

## Not implemented yet

There is no frontend, API gateway, authentication, authorization, rate limiting, Redis, AI/RAG investigation, OpenTelemetry instrumentation, Prometheus/Grafana setup, deployment orchestration, or explicit dead-letter and retry policy in the current repository. These remain potential future work, not current architecture components.
