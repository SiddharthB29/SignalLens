# Event Schema and API Reference

**Status:** Current implementation reference (November 2026)  
**Ingestion API version:** v1

This document describes the implemented event ingestion contract and the consumer's failure-group API. It calls out where behavior is currently narrower than a production reliability contract.

## Submit an event

`POST /api/v1/events` on the ingestion service (default `http://localhost:8080`). Send one JSON event per request with `Content-Type: application/json`.

```json
{
  "eventId": "evt-10001",
  "serviceName": "payment-service",
  "environment": "development",
  "occurredAt": "2026-10-25T06:30:00Z",
  "severity": "ERROR",
  "message": "Database connection timed out",
  "exceptionType": "SQLTransientConnectionException",
  "stackTrace": "Example stack trace",
  "traceId": "trace-abc123",
  "metadata": {
    "endpoint": "/api/payments",
    "region": "local"
  }
}
```

| Field | Type | Required | Implemented constraints and behavior |
| --- | --- | --- | --- |
| `eventId` | String | Yes | Non-blank; maximum 100 characters; trimmed. Used as the Kafka key and consumer-side event identity. |
| `serviceName` | String | Yes | Non-blank; maximum 100 characters; trimmed. |
| `environment` | Enum string | Yes | `DEVELOPMENT`, `STAGING`, or `PRODUCTION`; case-insensitive on input. |
| `occurredAt` | ISO 8601 date-time string | Yes | Must parse as `OffsetDateTime`, so a UTC offset or `Z` is required. |
| `severity` | Enum string | Yes | `WARN`, `ERROR`, or `FATAL`; case-insensitive on input. |
| `message` | String | Yes | Non-blank; maximum 2,000 characters; trimmed. |
| `exceptionType` | String or null | No | Maximum 200 characters; trimmed; blank becomes null. |
| `stackTrace` | String or null | No | Maximum 20,000 characters. Its content is not trimmed by the request DTO. |
| `traceId` | String or null | No | Maximum 128 characters; trimmed; blank becomes null. |
| `metadata` | JSON object or null | No | Maximum 50 top-level entries and nesting depth 5. The complete body remains subject to the 64 KiB limit. |

`receivedAt` is not part of the accepted request. The consumer records processing time when it creates a processed event. Metadata is accepted but is not currently included in the consumer's persisted processed-event response model.

### Responses

On successful Kafka publication, the service responds `202 Accepted`:

```json
{
  "status": "accepted",
  "eventId": "evt-10001",
  "message": "Event published successfully"
}
```

Other implemented outcomes:

| Status | Meaning |
| --- | --- |
| `400 Bad Request` | Malformed JSON, invalid enum/date-time, or request validation failure. Validation responses include `fieldErrors`; malformed input returns a general message. |
| `413 Payload Too Large` | Request exceeds 64 KiB (65,536 bytes), including bodies without a declared content length that exceed the streaming limit. |
| `503 Service Unavailable` | Kafka send failed, so the event was not acknowledged as accepted. |

The service does not currently require authentication, enforce a rate limit, compare duplicate event payloads, or return `409` for reused event IDs. A repeated submission can be published again; the consumer suppresses repeated `eventId` processing when its database record already exists. This consumer behavior is not an ingestion-side idempotency guarantee.

## Failure group API

The consumer service listens on port `8081`. Responses use Spring's page representation for list endpoints. `page` is zero-based; `size` defaults to 20 and must be between 1 and 100. Sorting may use one allowlisted property. Defaults are `lastSeenAt,desc` for groups and `occurredAt,desc` for occurrences.

### List failure groups

`GET /api/failure-groups`

Optional query parameters:

- `status`: `OPEN`, `ACKNOWLEDGED`, or `RESOLVED` (case-insensitive).
- `serviceName`: exact service name match after trimming.
- `environment`: `DEVELOPMENT`, `STAGING`, or `PRODUCTION` (case-insensitive).
- `page`, `size`, `sort`: Spring pageable parameters. Example: `?status=OPEN&page=0&size=20&sort=lastSeenAt,desc`.

Allowed group sort fields: `id`, `serviceName`, `environment`, `status`, `firstSeenAt`, `lastSeenAt`, `occurrenceCount`.

### Get a group and its occurrences

- `GET /api/failure-groups/{id}` returns `id`, `fingerprint`, `serviceName`, `environment`, `exceptionType`, `normalizedMessage`, `status`, `firstSeenAt`, `lastSeenAt`, and `occurrenceCount`.
- `GET /api/failure-groups/{id}/occurrences` returns a page of occurrences. Each includes `occurrenceId`, `eventId`, service, environment, severity, classification, message, `occurredAt`, `processedAt`, `createdAt`, and `traceId`.

Allowed occurrence sort fields: `id`, `occurredAt`, `createdAt`.

### Change group status

Each request is a `PATCH` with no request body:

- `PATCH /api/failure-groups/{id}/acknowledge`: `OPEN` → `ACKNOWLEDGED`.
- `PATCH /api/failure-groups/{id}/resolve`: `OPEN` or `ACKNOWLEDGED` → `RESOLVED`.
- `PATCH /api/failure-groups/{id}/reopen`: `RESOLVED` → `OPEN`.

The response is the updated failure-group object. IDs must be positive. Missing groups return 404, invalid parameters return 400, and disallowed or concurrently changed status transitions return 409. This API currently has no authentication or authorization.

## How grouping works

The consumer generates a SHA-256 fingerprint from service name, environment, exception type, and message normalized by stripping outer whitespace, lowercasing, and collapsing whitespace. It does not currently remove variable values such as IDs or timestamps from messages. Similar errors with different messages may therefore form separate groups.

Each successfully persisted event is stored once by `eventId` and linked to a failure group. A group count increases only when a new occurrence is inserted. Events are grouped after asynchronous Kafka delivery, so they may not yet appear in the API immediately after an ingestion response.

## Reliability and security notes

- `202` means Kafka acknowledged the producer send; it does not mean the consumer has committed the event to PostgreSQL or created a failure group.
- The current deployment uses a single local Kafka broker and does not configure an explicit outbox, dead-letter topic, application retry policy, or exactly-once delivery guarantee.
- PostgreSQL uniqueness and consumer-side inserts protect persisted event identity, but there is no durable ingestion record linking acceptance to publication.
- No credential redaction or authentication is implemented. Producers should avoid sending secrets in messages, stack traces, and metadata.
- Batch ingestion, arbitrary severity values, and API version negotiation are not implemented.
