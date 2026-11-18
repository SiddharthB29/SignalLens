# SignalLens

SignalLens is an incident intelligence backend that accepts application error events, publishes them to Kafka, processes them asynchronously, and groups similar failures for investigation.

## Current capabilities

- `event-ingestion` exposes `POST /api/v1/events`, validates event payloads, enforces a 64 KiB request limit, and returns `202 Accepted` after Kafka confirms the publish.
- `event-consumer` consumes `signallens.events`, records processed events in PostgreSQL, and groups repeated failures by a SHA-256 fingerprint.
- `event-consumer` exposes paginated failure group and occurrence APIs, plus acknowledge, resolve, and reopen actions.
- Docker Compose provisions local Kafka and PostgreSQL.

The React frontend, API gateway, authentication, rate limiting, Redis, AI investigation, and observability stack are not present yet.

## Repository layout

```text
docs/                       Architecture and event API documentation
infrastructure/docker/      Local Kafka and PostgreSQL Compose configuration
services/event-ingestion/   Event HTTP API and Kafka producer
services/event-consumer/    Kafka consumer, PostgreSQL persistence, failure APIs
```

## Requirements

- Java 25
- Docker with Docker Compose
- PostgreSQL credentials configured for the consumer

## Run local dependencies

From the repository root, create an `.env` file with the values expected by Compose:

```dotenv
POSTGRES_DB=signallens
POSTGRES_USER=signaladmin
POSTGRES_PASSWORD=choose-a-local-password
```

Start the dependencies:

```powershell
docker compose -f infrastructure/docker/docker-compose.yml up -d
```

The applications expect Kafka at `localhost:9092` and PostgreSQL at `127.0.0.1:5432`. Set the `signallens_db_pswrd` environment variable to the same PostgreSQL password before starting the consumer. The ingestion service listens on Spring Boot's default port `8080`; the consumer API listens on `8081`.

Run each service from its directory:

```powershell
cd services/event-ingestion
./mvnw spring-boot:run
```

```powershell
cd services/event-consumer
./mvnw spring-boot:run
```

Run a service's tests with `./mvnw test` from that service directory. Integration tests may require Docker for Testcontainers.

## API overview

Submit an event to `POST http://localhost:8080/api/v1/events`. Browse and manage failure groups through `http://localhost:8081/api/failure-groups`. Details, query parameters, event fields, validation, and current limitations are documented in [docs/event-schema.md](docs/event-schema.md). Component responsibilities and data flow are in [docs/architecture.md](docs/architecture.md).

## Project status

Backend ingestion, asynchronous processing, persistence, failure grouping, and management APIs are implemented. There is no frontend or production deployment configuration yet.
