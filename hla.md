# High-Level Architecture — Rules Engine Implementation

## Overview

Implement the 31 fraud detection rules defined in `rules.md`, wire them into the Kafka consumer pipeline, and publish classification results to a downstream Kafka topic.

---

## 1. Rule Interface & Category Classes

Introduce a `Rule` interface and one implementation class per rule category. `ClassifyingService` becomes an orchestrator that delegates to each category.

```
service/
├── ClassifyingService.java          # orchestrator — iterates rules, builds result
├── rules/
│   ├── Rule.java                    # interface
│   ├── VelocityRules.java           # V01–V05  (migrate existing logic out of ClassifyingService)
│   ├── AmountRules.java             # A01–A06
│   ├── TimeRules.java               # T01–T04
│   ├── EntryModeRules.java          # E01–E05
│   ├── ResponseCodeRules.java       # R01–R03
│   ├── MerchantRules.java           # M01–M03
│   ├── StructuringRules.java        # S01–S03
│   ├── TransferRules.java           # TR01–TR03
│   └── CompositeRules.java          # C01–C03
```

### Rule interface

```java
public interface Rule {
    List<FlagReason> evaluate(Transaction transaction);
}
```

Each category class is a Spring `@Component` implementing `Rule`. It receives `RuleConfig` (thresholds) and any required repository via constructor injection.

### ClassifyingService changes

- Inject `List<Rule>` (Spring auto-collects all `Rule` beans).
- `classify(Transaction)` iterates each rule, collects all triggered `FlagReason`s, sets `flaggedAbnormal` and `flagReasons` on the transaction, and returns the aggregated list.
- The existing velocity logic moves to `VelocityRules`; `ClassifyingService` itself holds no rule logic.

---

## 2. Transaction Model Expansion

Add fields from `CreditTransactionDto` that are required by the new rules but not yet mapped:

| Field                | Type       | Required by              |
|----------------------|------------|--------------------------|
| `isTransfer`         | `Boolean`  | E05, TR01–TR03, S02, C02 |
| `entryMode`          | `Integer`  | E01, E02, E04, C01, C02  |
| `authenticationFlag` | `Integer`  | E03–E05, A06, TR02, C01  |
| `responseCode`       | `int`      | R01–R03                  |
| `terminalId`         | `Integer`  | M03                      |
| `zScore`             | `Double`   | A01, C01, C03            |
| `ratioToMedian`      | `Double`   | A02                      |
| `transactionCode`    | `long`     | (future use)             |

Update `Transaction.fromDto()` and `TransactionTest` accordingly.

> **Note**: `zScore` and `ratioToMedian` are not yet on `CreditTransactionDto`. They are computed by the ingestion service and carried on `FatTransactionDto`. Until the DTO is extended, the Amount A01/A02 rules and composite C03 will be **stubbed** (return empty list with a TODO).

---

## 3. Rule Implementation Tiers

### Tier 1 — Fully implementable now (DTO + existing Redis)

These rules use only fields already on the DTO or can be served by extending `VelocityRepository`:

| Rule   | Category  | Data source                  |
|--------|-----------|------------------------------|
| V01–V05| Velocity  | DTO enrichment + Redis       |
| A04    | Amount    | `amount` (round-number check)|
| A05    | Amount    | `amount`, `velocity1H`, `secondsSinceLastTransaction` |
| A06    | Amount    | `amount`, `authenticationFlag` |
| T01    | Time      | `isNight` / `processedAt`    |
| T02    | Time      | `secondsSinceLastTransaction`|
| E01    | Entry     | `entryMode`                  |
| E02    | Entry     | `entryMode`                  |
| E03    | Entry     | `authenticationFlag`, `amount` |
| E04    | Entry     | `authenticationFlag`, `entryMode`, `amount` |
| E05    | Entry     | `isTransfer`, `authenticationFlag` |
| M02    | Merchant  | `merchantAcquirer`, `isTransfer` |
| TR02   | Transfer  | `isTransfer`, `amount`, `authenticationFlag` |
| C02    | Composite | `entryMode`, `authenticationFlag`, `isTransfer`, `destinationCard`, `amount` |

**14 rules fully implementable** (plus the 5 existing velocity rules = 19 total).

### Tier 2 — Implementable with new Redis tracking

These rules need new Redis data structures for historical lookups:

| Rule  | What's needed in Redis                                    |
|-------|-----------------------------------------------------------|
| R01   | Recent response codes per card (sorted set, 30-min window)|
| R02   | 24H response code history per card                        |
| R03   | Wrong-PIN count per card (1H window)                      |
| T03   | `secondsSinceLastTransaction` already on DTO (just needs large-value check) — **actually Tier 1** |
| S02   | Distinct destination card count per source card (1H window)|

Extend `VelocityRepository` with:
- `recordResponseCode(sourceCard, responseCode, epochSecond)` — sorted set keyed by card, score = timestamp, value = responseCode+txId
- `getRecentDeclineCount(sourceCard, windowSeconds)` — count non-00 responses in window
- `getWrongPinCount(sourceCard, windowSeconds)` — count responseCode==55 in window
- `recordDestination(sourceCard, destinationCard, epochSecond)` — sorted set for fan-out tracking
- `getDistinctDestinationCount1H(sourceCard)` — cardinality of destination set

### Tier 3 — Stubbed (require external data not yet available)

| Rule   | What's missing                                    |
|--------|---------------------------------------------------|
| A01    | `zScore` not on current DTO                       |
| A02    | `ratioToMedian` not on current DTO                |
| A03    | Card's historical max transaction amount           |
| T04    | Historical transaction count (first-tx detection)  |
| V05*   | Already implemented via Redis 30D count            |
| M01    | MCC lookup from `merchantAcquirer`                 |
| M03    | Expected terminal ID per merchant mapping          |
| S01    | Per-card daily total aggregation                   |
| S03    | Inbound vs outbound transfer sum (24H)             |
| TR01   | Destination card abnormal state lookup              |
| TR03   | Previous transaction's details (relay detection)    |
| C01    | Composite score — partially implementable (weights for available signals only) |
| C03    | Depends on `zScore`                                |

These will be present as methods returning `Collections.emptyList()` with `// TODO` annotations explaining what's needed.

---

## 4. RuleConfig Expansion

Add threshold constants for all new rules to `RuleConfig.java`, following the existing `@Value` pattern:

```yaml
rules:
  velocity:
    # (existing)
  amount:
    round-number-min: 200
    round-number-high-min: 500
    micro-charge-max: 1.00
    auth-threshold-low-min: 45.00
    auth-threshold-low-max: 49.99
    auth-threshold-high-min: 95.00
    auth-threshold-high-max: 99.99
    z-score-strong: 3.0
    z-score-moderate: 2.5
    ratio-to-median-flag: 5.0
    ratio-to-median-high: 10.0
  time:
    night-start-hour: 1
    night-end-hour: 5
    rapid-sequential-high-seconds: 30
    rapid-sequential-moderate-seconds: 120
    dormant-days-high: 90
    dormant-amount-threshold: 300
    dormant-days-moderate: 180
  entry:
    no-auth-amount-threshold: 100
    no-auth-high-amount-threshold: 500
    no-auth-cnp-amount-threshold: 200
  response:
    decline-then-approve-window-minutes: 30
    decline-then-approve-min-declines: 2
    high-decline-min-count: 3
    high-decline-rate: 0.5
    high-decline-absolute: 5
    wrong-pin-spike-threshold: 2
  merchant:
    # high-risk MCCs (comma-separated list)
    high-risk-mccs: 7995,6211,6051,4829,6010,6011
  structuring:
    fan-out-distinct-destinations: 3
    fan-out-total-threshold: 1000
  transfer:
    unauth-large-threshold: 1000
    unauth-null-threshold: 500
  composite:
    impossible-chip-no-auth-amount: 25
    impossible-cnp-no-auth-amount: 200
    night-burst-min-velocity: 3
    night-burst-min-zscore: 2.0
```

---

## 5. Kafka Producer — Classification Output

### Topic

`classification-results`

### Output DTO

```java
public record ClassificationResultDto(
    @JsonProperty("transaction_internal_id") Long transactionInternalId,
    @JsonProperty("flagged_abnormal") boolean flaggedAbnormal,
    @JsonProperty("flag_reasons") List<String> flagReasons,
    @JsonProperty("classified_at") LocalDateTime classifiedAt,
    @JsonProperty("source_card") Long sourceCard,
    @JsonProperty("amount") BigDecimal amount
) {}
```

### Producer configuration

Add to `application.yaml`:

```yaml
spring:
  kafka:
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

### ClassificationProducerService

New `service/messaging/ClassificationProducerService.java`:

```java
@Service
public class ClassificationProducerService {
    private final KafkaTemplate<String, ClassificationResultDto> kafkaTemplate;

    public void publish(ClassificationResultDto result) {
        kafkaTemplate.send("classification-results",
            result.transactionInternalId().toString(),
            result);
    }
}
```

Key = `transactionInternalId` (string) for partition affinity.

---

## 6. Pipeline Wiring

Update `FatTransactionConsumerService.listen()`:

```
1. Parse Debezium message → CreditTransactionDto        (existing)
2. Transaction.fromDto(dto)                               (existing, not yet called)
3. classifyingService.classify(transaction)                (new)
4. Build ClassificationResultDto from transaction          (new)
5. classificationProducerService.publish(result)           (new)
```

The consumer already has retry logic (`@RetryableTopic`). Classification and publishing happen within the same retry scope — if publishing fails, the entire message is retried.

---

## 7. File Change Summary

| File | Action |
|------|--------|
| `service/rules/Rule.java` | **New** — interface |
| `service/rules/VelocityRules.java` | **New** — migrate V01–V05 from ClassifyingService |
| `service/rules/AmountRules.java` | **New** — A04, A05, A06 (A01–A03 stubbed) |
| `service/rules/TimeRules.java` | **New** — T01, T02, T03 (T04 stubbed) |
| `service/rules/EntryModeRules.java` | **New** — E01–E05 |
| `service/rules/ResponseCodeRules.java` | **New** — R01–R03 (need Redis extension) |
| `service/rules/MerchantRules.java` | **New** — M02 (M01, M03 stubbed) |
| `service/rules/StructuringRules.java` | **New** — S02 (S01, S03 stubbed) |
| `service/rules/TransferRules.java` | **New** — TR02 (TR01, TR03 stubbed) |
| `service/rules/CompositeRules.java` | **New** — C02 (C01, C03 stubbed) |
| `service/ClassifyingService.java` | **Modify** — become orchestrator, delegate to Rule beans |
| `model/Transaction.java` | **Modify** — add 8 fields, update `fromDto()` |
| `config/RuleConfig.java` | **Modify** — add all new thresholds |
| `dto/ClassificationResultDto.java` | **New** — output event |
| `service/messaging/ClassificationProducerService.java` | **New** — Kafka producer |
| `service/messaging/FatTransactionConsumerService.java` | **Modify** — wire classify + publish |
| `repository/VelocityRepository.java` | **Modify** — add response code & destination tracking |
| `application.yaml` | **Modify** — add producer config, rule defaults |
| Tests | **New/Modify** — unit tests for each rule class, producer, pipeline integration |

---

## 8. Implementation Order

1. **Transaction model expansion** — add fields, update `fromDto()`, fix tests
2. **RuleConfig expansion** — add all threshold constants
3. **Rule interface + VelocityRules** — extract existing logic, verify tests still pass
4. **Tier 1 rule classes** — AmountRules, TimeRules, EntryModeRules, MerchantRules, TransferRules, CompositeRules
5. **VelocityRepository extensions** — response code tracking, destination tracking
6. **Tier 2 rule classes** — ResponseCodeRules, StructuringRules (fan-out)
7. **Tier 3 stubs** — all remaining rules as empty methods with TODOs
8. **ClassifyingService refactor** — orchestrator pattern with `List<Rule>`
9. **ClassificationResultDto + Producer** — output event and Kafka publishing
10. **Pipeline wiring** — connect consumer → classify → publish
11. **Tests** — unit tests per rule class, integration test for full pipeline
