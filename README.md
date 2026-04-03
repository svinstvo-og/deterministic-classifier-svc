# deterministic-classifier-svc

Part of a personal fraud detection backend project (varta). Consumes Debezium CDC events from Kafka, parses them into POJOs, runs transactions through deterministic rules/equations, and classifies them as normal or malicious. Emits classification results to downstream Kafka consumers.

## Stack

- Java 21, Spring Boot 3.4.1, Gradle
- Spring Kafka (consumer + producer)
- Spring Data Redis (velocity tracking)
- Spring Retry (consumer retry with exponential backoff)
- Lombok
- Jackson

## Architecture

Kafka (Debezium CDC) → FatTransactionConsumerService
                             ↓
                      CreditTransactionMapper (JSON → DebeziumEnvelope<CreditTransactionDto>)
                             ↓
                      Transaction.fromDto() (DTO → domain model)
                             ↓
                      ClassifyingService (rules engine — WIP)
                             ↓
                      Kafka (downstream classification event)

### Key packages

- config/ — Spring beans (ObjectMapper, etc.)
- dto/ — Debezium envelope wrappers + CreditTransactionDto (record, snake_case JSON via @JsonProperty)
- model/ — Transaction domain object (Lombok builder, static fromDto() factory)
- service/ — ClassifyingService (WIP), service/messaging/FatTransactionConsumerService
- util/ — CreditTransactionMapper

### Debezium message structure

DebeziumEnvelope<T> → DebeziumPayload<T> (before, after, op, ts_ms) → CreditTransactionDto

The mapper extracts the .after field from the payload.

## Design decisions

- Rules: Hard-coded rule functions in the codebase. Any constants/thresholds used in rules live in one place (a config class or provider) and are injected wherever needed.
- Redis: Used for velocity tracking (e.g. transaction counts per card in a rolling time window).
- Classification output: Exact event schema TBD, but results go to a downstream Kafka topic.
- FlagReason enum: Currently a placeholder (FOO). Will expand as rules are added.

## Running locally

- Kafka: localhost:9092
- Consumer group: deterministic-classifying-svc
- Listens on topic: dbserver1.public.credit_trans
- Server port: 8085

## Conventions

- DTOs are Java records with @JsonProperty for snake_case mapping
- Domain models use Lombok @Builder + static factory fromDto()
- Kafka consumer uses @RetryableTopic or Spring Retry with 3 attempts, exponential backoff (1s initial, 2x multiplier)
- ObjectMapper configured with FAIL_ON_UNKNOWN_PROPERTIES = false
