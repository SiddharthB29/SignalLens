# SignalLens — Initial Architecture

## 1. Project Overview

SignalLens is an AI-assisted incident intelligence platform that ingests application error events, groups related errors into incidents, and helps developers investigate failures using relevant technical documentation.

## 2. Architecture Goals

- Separate event ingestion from incident processing.
- Process events asynchronously.
- Prevent duplicate events from creating duplicate incident records.
- Persist incident data reliably.
- Provide APIs for incident management.
- Support evidence-based AI investigation.
- Make failures observable and testable.
- Keep local development manageable for a solo developer.

## 3. Core Components

### Event Ingestion Service

Receives error events over HTTP, validates incoming payloads, applies appropriate request limits, and publishes accepted events to Kafka.

### Kafka

Transports events asynchronously between ingestion and incident processing. Supports consumer groups, retries, and recovery strategies.

### Incident Service

Consumes events, computes fingerprints, groups related errors into incidents, persists events and incidents, and manages incident lifecycle state.

### PostgreSQL

Stores durable application data. Each service owns its data and schema or database boundary.

### Redis

Provides caching, rate limiting, and temporary state when justified by the implementation.

### API Gateway

Routes dashboard requests to the appropriate backend service and integrates with the security model.

### Frontend

A React and TypeScript dashboard for viewing incidents, investigating timelines, and managing incident status.

### AI Investigation Service

Uses Spring AI and retrieval-augmented generation to retrieve relevant runbooks and propose evidence-based investigation steps.

### Observability

Uses structured logs, correlation identifiers, metrics, traces, Prometheus, Grafana, and OpenTelemetry to understand system behavior.

## 4. Primary Event Flow

1. A simulated application generates an error event.
2. The application submits the event to the Event Ingestion Service.
3. The ingestion service validates the event and publishes it to Kafka.
4. The Incident Service consumes the event.
5. The service computes an error fingerprint and determines whether an incident already exists.
6. Incident and event records are persisted in PostgreSQL.
7. The dashboard retrieves incident information through backend APIs.
8. The AI Investigation Service retrieves relevant documentation and analyzes available incident evidence.

## 5. Reliability Requirements

- Validate incoming events.
- Use stable event identifiers for deduplication.
- Make event processing idempotent.
- Define bounded retry and dead-letter handling.
- Handle failures between Kafka and PostgreSQL.
- Track processing latency and failures.
- Avoid claiming successful durable acceptance unless the configured delivery guarantees support that claim.

## 6. Security Requirements

- Authenticate and authorize protected APIs.
- Validate and limit request sizes.
- Avoid storing secrets in source control.
- Avoid logging credentials or sensitive payload contents.
- Protect internal service endpoints.
- Restrict AI investigation to authorized incident data.

## 7. Development Strategy

Build and test the system incrementally.

Start with the Event Ingestion Service and its validation rules. Introduce Kafka after the basic REST endpoint is working. Add incident persistence and correlation before implementing advanced reliability patterns, the frontend, observability, and AI investigation.

## 8. Initial Non-Goals

- Kubernetes deployment.
- Multi-region infrastructure.
- A production-grade enterprise identity platform.
- Autonomous remediation of incidents.
- Multiple databases or messaging platforms without a demonstrated need.

These features may be considered later if they provide clear engineering value.
