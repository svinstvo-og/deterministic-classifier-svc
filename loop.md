# Closing the Classification Loop — High-Level Plan

## Current State

The pipeline is broken in the middle. The consumer (`FatTransactionConsumerService`)
deserializes Debezium CDC events into `CreditTransactionDto` and stops there.
`ClassifyingService` and all 4 rule classes exist but are never called.
There is no Kafka producer configured.

```
Kafka IN  →  Mapper  →  [DEAD END]

ClassifyingService + Rules  →  [ORPHANED, never invoked]
```

## Target State

```
Kafka IN (dbserver1.public.credit_trans)
    → FatTransactionConsumerService.listen()
    → CreditTransactionMapper.fromDebezium()  →  CreditTransactionDto
    → Transaction.fromDto()                   →  Transaction (domain)
    → ClassifyingService.classify()           →  List<FlagReason>
    → Build ClassificationResultDto
    → Kafka OUT (deterministic-classification)
```

Every consumed event goes through classification and produces an output event,
whether flagged or not (downstream consumers decide what to do with clean transactions).

## Work Items

### 1. Create `ClassificationResultDto`

A lightweight record carrying only the classification outcome:

- `transactionInternalId` (Long)
- `flaggedAbnormal` (Boolean)
- `flagReasons` (List<FlagReason>)
- `classifiedAt` (Instant — when classification ran)

### 2. Add Kafka producer config

- Add producer properties to `application.yaml` (key: String serializer, value: JSON serializer).
- Define the output topic name `deterministic-classification` as a config property.

### 3. Create `ClassificationProducerService`

A Spring `@Service` that wraps `KafkaTemplate<String, ClassificationResultDto>` and exposes
a `publish(ClassificationResultDto)` method. Key is `transactionInternalId` as string.

### 4. Wire the consumer end-to-end

Update `FatTransactionConsumerService` to:

1. Map JSON → `CreditTransactionDto` (already done)
2. Convert DTO → `Transaction` via `Transaction.fromDto()`
3. Call `ClassifyingService.classify(transaction)`
4. Build a `ClassificationResultDto` from the result
5. Call `ClassificationProducerService.publish(result)`

### 5. Stubbed rules

No action needed. A01–A03, T04, S01, S03 already return empty lists and will
participate as no-ops until their implementations are filled in.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Output event shape | Lightweight (ID + flag + reasons) | Keeps topic lean; downstream joins if it needs full data |
| Output topic | `deterministic-classification` | Leaves room for future ML-classifier topics |
| Publish clean txns? | Yes | Downstream can filter; avoids silent data gaps |
| Stubbed rules | Run as no-ops | They already return `[]`; zero extra work |