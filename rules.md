# Fraud Detection Rules — deterministic-classifier-svc

Research and design document for the `ClassifyingService` rules engine.

## Model Field Reference

Fields currently on `Transaction` (mapped from `CreditTransactionDto.fromDto()`):

| Field | Type | Source |
|---|---|---|
| `transactionInternalId` | `Long` | DTO |
| `processedAt` | `LocalDateTime` | DTO |
| `amount` | `BigDecimal` | DTO `transactionAmount` |
| `destinationCard` | `Long` | DTO |
| `merchantAcquirer` | `Long` | DTO |
| `abnormal` | `Boolean` | DTO (statistical label, not for classifier use) |
| `flaggedAbnormal` | `Boolean` | Set by classifier |
| `flagReason` | `FlagReason` | Set by classifier |

Fields available in `CreditTransactionDto` not yet mapped to `Transaction` but useful for rules:

| Field | Type | Notes |
|---|---|---|
| `sourceCard` | `Long` | Card initiating the transaction |
| `isTransfer` | `Boolean` | True if card-to-card transfer |
| `entryMode` | `Integer` | ISO 8583 point-of-entry code |
| `authenticationFlag` | `Integer` | Whether PIN/3DS was used |
| `responseCode` | `int` | ISO 8583 authorization response code |
| `terminalTypeCode` | `Integer` | Terminal category |
| `terminalId` | `Integer` | Specific terminal identifier |
| `transactionCode` | `long` | Transaction type code |
| `transactionDescription` | `String` | Free-text description |
| `abnormalState` | `int` | Pre-computed abnormal state from ingestion |

Pre-computed enrichment fields available via the ingestion service (carried on `FatTransactionDto`):

| Field | Notes |
|---|---|
| `velocity1H` | Transaction count for this card in the last 1 hour |
| `velocity24H` | Transaction count for this card in the last 24 hours |
| `zScore` | Z-score of `transactionAmount` vs. this card's 30-day history |
| `ratioToMedian` | `transactionAmount / median(cardHistory)` |
| `isNight` | True if `processedAt` hour is between 01:00 and 05:00 |
| `secondsSinceLastTransaction` | Seconds elapsed since previous transaction on this card |

---

## Rule Catalog

Rules are grouped by category. Each rule lists the fields it needs, the logic, recommended `FlagReason` enum value, and a brief explanation.

---

### Category V — Velocity Rules

High-frequency activity in short windows is the most common automated fraud signal.

---

#### RULE-V01: High Transaction Count — 1 Hour

**Fields**: `velocity1H`

**Logic**:
```
if velocity1H >= 5 → flag
```

**FlagReason**: `HIGH_VELOCITY_1H`

Genuine cardholders average 2–4 transactions per day. Five or more within a single hour is statistically extreme. Stolen-card fraud attempts to extract maximum value before cancellation, producing exactly this pattern.

---

#### RULE-V02: High Transaction Count — 24 Hours

**Fields**: `velocity24H`

**Logic**:
```
if velocity24H >= 15 → flag
```

**FlagReason**: `HIGH_VELOCITY_24H`

Fifteen or more transactions in 24 hours exceeds the 99th percentile of legitimate cardholder behavior. Card compromise operations typically sustain elevated activity across 12–24 hours before the card is blocked.

---

#### RULE-V03: High Distinct Merchant Count — 1 Hour

**Fields**: `velocity1H`, distinct merchant count in window

**Logic**:
```
if distinctMerchants1H >= 4 → flag
```

**FlagReason**: `HIGH_MERCHANT_SPREAD_1H`

A single merchant with many transactions could be a restaurant with multiple tabs. Four or more *distinct* merchants in one hour is physically implausible for a human. In card-not-present (CNP) fraud, bots hit many merchants in rapid succession. This rule is independent of total count.

---

#### RULE-V04: Rapid-Fire Micro-Interval Burst

**Fields**: `secondsSinceLastTransaction`, `velocity1H`

**Logic**:
```
if secondsSinceLastTransaction < 60 && velocity1H >= 2 → flag
```

**FlagReason**: `RAPID_SEQUENTIAL_TRANSACTIONS`

Automated card-testing scripts submit authorizations in rapid succession. Humans cannot physically complete separate purchases at different merchants within 60 seconds. Sub-minute inter-transaction intervals combined with two or more transactions in the current hour is characteristic of bot activity.

---

#### RULE-V05: Velocity Spike Ratio vs. Historical Baseline

**Fields**: `velocity1H`, card's historical average transactions per hour

**Logic**:
```
historicalAvgPerHour = totalTransactionsLast30D / (30 * 24)
if velocity1H > historicalAvgPerHour * 10 → flag
```

**FlagReason**: `VELOCITY_SPIKE`

A card averaging 0.1 transactions/hour suddenly producing 5+ is a 50× spike, characteristic of account takeover or physical theft. Normalizing against each card's own baseline avoids penalizing naturally high-frequency cardholders.

---

### Category A — Amount Anomaly Rules

---

#### RULE-A01: Z-Score Outlier

**Fields**: `zScore` (pre-computed by ingestion service)

**Logic**:
```
if zScore >= 3.0 → flag (strong)
if zScore >= 2.5 → flag (moderate — combine with other signals)
```

**FlagReason**: `AMOUNT_Z_SCORE_OUTLIER`

Z-score 3.0 corresponds to the 99.87th percentile of a normal distribution. The amount is more than 3 standard deviations above this card's historical mean. Normalizing per card is essential — $500 is unremarkable for a high-spender but alarming for a card averaging $30/transaction.

---

#### RULE-A02: Ratio-to-Median Spike

**Fields**: `ratioToMedian` (pre-computed by ingestion service)

**Logic**:
```
if ratioToMedian >= 5.0 → flag
if ratioToMedian >= 10.0 → high-priority flag
```

**FlagReason**: `AMOUNT_RATIO_TO_MEDIAN_SPIKE`

Median is more robust than mean to outlier contamination. A ratio of 5× means this transaction is 5 times the card's typical purchase. Fraudsters who learn a card's average spend may not realize how different the amount is relative to the median.

---

#### RULE-A03: All-Time Maximum Exceeded

**Fields**: `amount`, card's historical maximum single transaction

**Logic**:
```
if amount > historicalMax * 2 → flag
if amount > historicalMax * 5 → high-priority flag
```

**FlagReason**: `AMOUNT_EXCEEDS_HISTORICAL_MAX`

A fraudster who knows the card's average spend may not know its transaction ceiling. Doubling the all-time maximum is a strong ceiling-violation signal.

---

#### RULE-A04: Round Number Amount

**Fields**: `amount`

**Logic**:
```
if amount % 100 == 0 && amount >= 200 → suspicious
if amount % 500 == 0 && amount >= 500 → flag
```

**FlagReason**: `ROUND_NUMBER_AMOUNT`

Real purchases at retail merchants almost never produce perfectly round numbers. Fraudulent transfers, money mule payouts, and manual card testing commonly use round amounts. Weight increases with amount magnitude.

---

#### RULE-A05: Micro-Charge Card Testing

**Fields**: `amount`, `velocity1H`, `secondsSinceLastTransaction`

**Logic**:
```
if amount < 1.00 && velocity1H >= 2 → flag
if amount < 1.00 && secondsSinceLastTransaction < 300 → flag
```

**FlagReason**: `MICRO_CHARGE_CARD_TESTING`

Fraudsters who acquire card numbers in bulk "test" them with trivial charges ($0.01–$1.00) to identify live cards before attempting large withdrawals. A sub-dollar charge followed by further transactions within 5 minutes is a classic card-probing signature.

---

#### RULE-A06: Amount Just Below Authentication Threshold

**Fields**: `amount`, `authenticationFlag`

**Logic**:
```
# Common contactless / no-auth limits: $25, $50, $100 (country-dependent)
if amount >= 45.00 && amount <= 49.99 && authenticationFlag == 0 → flag
if amount >= 95.00 && amount <= 99.99 && authenticationFlag == 0 → flag
```

**FlagReason**: `AMOUNT_BELOW_AUTH_THRESHOLD`

Fraudsters know the floor for mandatory PIN/signature/3DS authentication and deliberately keep transactions just under it to avoid triggering step-up challenges. This is sometimes called "threshold gaming."

---

### Category T — Time-Based Rules

---

#### RULE-T01: Night Transaction

**Fields**: `isNight` (pre-computed), or `processedAt.getHour()`

**Logic**:
```
if processedAt.getHour() >= 1 && processedAt.getHour() <= 5 → isNight = true → flag
```

**FlagReason**: `NIGHT_TRANSACTION`

Night transactions (01:00–05:00) have 2–3× baseline fraud rates in published industry studies. Most legitimate cardholders are asleep. Automated fraud operations and cross-timezone fraudsters do not observe business hours. Weight this higher when combined with other signals.

---

#### RULE-T02: Rapid Sequential Transactions

**Fields**: `secondsSinceLastTransaction`

**Logic**:
```
if secondsSinceLastTransaction < 30 → high-priority flag
if secondsSinceLastTransaction < 120 → moderate flag (combine with other signals)
```

**FlagReason**: `RAPID_SEQUENTIAL_TRANSACTIONS`

Two purchases from the same card 20 seconds apart implies either scripted attack or POS terminal replay. Physical humans cannot complete separate purchases at separate merchants within 30 seconds.

---

#### RULE-T03: Transaction After Long Dormancy

**Fields**: `secondsSinceLastTransaction` (convert to days), `amount`

**Logic**:
```
daysSinceLastTx = secondsSinceLastTransaction / 86400
if daysSinceLastTx > 90 && amount > 300 → flag
if daysSinceLastTx > 180 → moderate flag
```

**FlagReason**: `DORMANT_CARD_REACTIVATION`

Dormant card numbers appearing in data breaches are often tested months later, after the legitimate cardholder has stopped monitoring the account. A card inactive for 3+ months suddenly making a high-value purchase is anomalous.

---

#### RULE-T04: First Transaction is High-Value

**Fields**: `velocity30D` (or equivalent historical count), `amount`

**Logic**:
```
if historicalTransactionCount <= 1 && amount > 500 → flag
if historicalTransactionCount == 0 && amount > 200 && isTransfer → flag
```

**FlagReason**: `NEW_CARD_HIGH_VALUE_FIRST_TX`

Legitimate new cardholders start with small, familiar purchases. Fraudsters using synthetic identity accounts or intercepted new cards go immediately for maximum extraction. A first-ever transaction being a large purchase or transfer is a strong early-life fraud signal.

---

### Category E — Entry Mode and Authentication Rules

`entryMode` uses ISO 8583 Point-of-Entry codes. Key values:

| Code | Meaning |
|---|---|
| `01` | Manual key-entry (card not present) |
| `02` | Magnetic stripe read |
| `05` | EMV chip (contact) |
| `07` | EMV contactless / NFC |
| `10` | Credential on file (stored card) |
| `79` | Chip card, fallback to magnetic stripe |
| `90` | Magnetic stripe (full track data) |

---

#### RULE-E01: Card Not Present (Keyed Entry)

**Fields**: `entryMode`

**Logic**:
```
if entryMode == 01 → flag
```

**FlagReason**: `CARD_NOT_PRESENT`

Keyed CNP is the dominant fraud vector — the physical card is not required, so a stolen card number alone suffices. CNP fraud accounts for over 70% of payment fraud losses in EMV-chip markets.

---

#### RULE-E02: Chip Card Fallback to Magnetic Stripe

**Fields**: `entryMode`

**Logic**:
```
if entryMode == 79 || entryMode == 80 → flag
```

**FlagReason**: `CHIP_FALLBACK_TO_MAGSTRIPE`

Legitimate chip fallback is rare — modern terminals read chips reliably. Fraudsters who clone cards can only clone the magnetic stripe, not the chip's cryptogram. They deliberately damage or shim the chip area to force magstripe fallback, bypassing EMV protection entirely. This is one of the highest-specificity individual signals.

---

#### RULE-E03: No Authentication on High-Value Transaction

**Fields**: `authenticationFlag`, `amount`

**Logic**:
```
if authenticationFlag == 0 && amount > 100 → flag
if authenticationFlag == 0 && amount > 500 → high-priority flag
```

**FlagReason**: `NO_AUTH_HIGH_VALUE`

Strong Customer Authentication (SCA) regulations (PSD2 in Europe, network mandates globally) require authentication above certain amounts. A high-value transaction bypassing authentication suggests terminal misconfiguration exploitation or use of a stolen card at a low-security terminal.

---

#### RULE-E04: No Authentication on CNP Transaction

**Fields**: `authenticationFlag`, `entryMode`, `amount`

**Logic**:
```
if authenticationFlag == 0 && entryMode == 01 && amount > 200 → flag
```

**FlagReason**: `NO_AUTH_CNP`

Keyed CNP transactions above the SCA threshold must have at least 3DS or CVC2 verification. A null or zero auth flag on a high-value CNP transaction represents a processing anomaly consistent with fraud bypass.

---

#### RULE-E05: Unauthenticated Transfer

**Fields**: `isTransfer`, `authenticationFlag`

**Logic**:
```
if isTransfer == true && (authenticationFlag == 0 || authenticationFlag == null) → flag
```

**FlagReason**: `UNAUTHENTICATED_TRANSFER`

Transfers are high-value, irreversible, and the primary money-out mechanism in account takeover fraud. An unauthenticated transfer is definitionally anomalous under bank security policies.

---

### Category R — Response Code Rules

`responseCode` uses ISO 8583 codes. Key values:

| Code | Meaning |
|---|---|
| `00` | Approved |
| `05` | Do not honor (generic decline) |
| `14` | Invalid card number |
| `51` | Insufficient funds |
| `54` | Expired card |
| `55` | Incorrect PIN |
| `57` | Transaction not permitted |

---

#### RULE-R01: Declined-Then-Approved Pattern

**Fields**: `responseCode`, recent response history for `sourceCard`

**Logic**:
```
recentDeclines = count of non-00 responses for this card in last 30 minutes
if recentDeclines >= 2 && current responseCode == 00 → flag
```

**FlagReason**: `DECLINED_THEN_APPROVED`

A fraudster testing stolen card numbers receives many declines before finding a live card. The sequence "decline, decline, approve" is textbook card-testing behavior. This pattern is sometimes called "brute-force approval."

---

#### RULE-R02: Excessive Decline Rate

**Fields**: `responseCode`, transaction history for `sourceCard`

**Logic**:
```
totalTx24H = count for card in last 24h
declinedTx24H = count where responseCode != 00 in last 24h
if declinedTx24H >= 3 && (declinedTx24H / totalTx24H) >= 0.5 → flag
if declinedTx24H >= 5 → flag regardless of rate
```

**FlagReason**: `HIGH_DECLINE_RATE`

A genuine cardholder declined once typically contacts their bank and stops. Multiple declines indicate card testing or repeated unauthorized access attempts.

---

#### RULE-R03: Wrong PIN Spike

**Fields**: `responseCode`

**Logic**:
```
wrongPinCount = count of responseCode == 55 for this card in last 1h
if wrongPinCount >= 2 → flag
```

**FlagReason**: `WRONG_PIN_SPIKE`

After stealing a physical card, fraudsters attempt to guess the PIN at ATMs or high-value POS terminals. Most networks lock the card after 3 wrong PINs; catching 2 wrong attempts enables early intervention.

---

### Category M — Merchant and Terminal Rules

---

#### RULE-M01: High-Risk Merchant Category

**Fields**: `merchantAcquirer` (cross-referenced merchant category code — MCC)

**High-risk MCCs** (per Visa/Mastercard published guidance):
- `7995` — Gambling / betting / casino
- `6211` — Securities dealers
- `6051` — Cryptocurrency / quasi-cash exchanges
- `4829` — Wire transfer money orders
- `6010` / `6011` — Cash advances / ATM

**Logic**:
```
HIGH_RISK_MCCS = {7995, 6211, 6051, 4829, 6010, 6011}
if merchantCategoryCode in HIGH_RISK_MCCS → flag
if merchantCategoryCode in HIGH_RISK_MCCS && amount > 200 → high-priority flag
```

**FlagReason**: `HIGH_RISK_MERCHANT_CATEGORY`

High-risk MCCs produce liquid assets directly (cash, crypto, gambling chips) or have looser fraud controls. Transactions at gambling and wire-transfer merchants have 5–10× baseline fraud rates.

---

#### RULE-M02: Null Merchant on Non-Transfer Transaction

**Fields**: `merchantAcquirer`, `isTransfer`

**Logic**:
```
if merchantAcquirer == null && isTransfer == false → flag
```

**FlagReason**: `NULL_MERCHANT`

Every purchase transaction must have an identifiable merchant. A null merchant on a purchase indicates data suppression, characteristic of certain POS skimming attacks or fraudulent terminal registrations.

---

#### RULE-M03: Terminal ID Mismatch

**Fields**: `terminalId`, expected terminal for `merchantAcquirer`

**Logic**:
```
if terminalId != null && merchantAcquirer.expectedTerminalId != null
   && terminalId != merchantAcquirer.expectedTerminalId → flag
```

**FlagReason**: `TERMINAL_ID_MISMATCH`

Every registered POS terminal has a fixed terminal ID assigned by the acquirer. A transaction arriving with a terminal ID that does not match the merchant's registered terminal may indicate a rogue device introduced by attackers (physical skimmer with modified terminal ID).

---

### Category S — Structuring Rules

Structuring (also called smurfing) involves deliberately keeping amounts below detection thresholds.

---

#### RULE-S01: Sub-Threshold Structuring

**Fields**: `amount`, `velocity24H`

**Logic**:
```
# US Bank Secrecy Act threshold: $10,000
if amount >= 9000 && amount < 10000 → suspicious
if multiple such transactions in 24h → flag

# Internal daily limit structuring
if every transaction in 24h is < 999.99 but total > 3000 → flag
```

**FlagReason**: `STRUCTURING_SUB_THRESHOLD`

The Bank Secrecy Act requires reporting cash transactions above $10,000. Structuring to avoid this threshold is itself a federal offense. The same tactic is used within card fraud to keep charges below issuer fraud-rule limits.

---

#### RULE-S02: Fan-Out Transfer Pattern

**Fields**: `isTransfer`, `destinationCard`, distinct destination count in window

**Logic**:
```
distinctDestinations1H = distinct destinationCard values for this card in last 1h
totalTransferred1H = sum of amount where isTransfer == true in last 1h
if isTransfer == true && distinctDestinations1H >= 3 → flag
if totalTransferred1H > 1000 && distinctDestinations1H >= 2 → flag
```

**FlagReason**: `FAN_OUT_TRANSFER`

Money mule networks receive stolen funds into one account and rapidly fan out small transfers to dozens of mule accounts to obscure the trail. This smurfing pattern is a hallmark of organized fraud.

---

#### RULE-S03: Accumulation-Then-Transfer (Pass-Through Account)

**Fields**: `isTransfer`, `sourceCard`, `destinationCard`, time-ordered sequences

**Logic**:
```
inbound24H = sum of amount where destinationCard == thisCard && isTransfer == true in last 24h
outbound24H = sum of amount where sourceCard == thisCard && isTransfer == true in last 24h
if outbound24H >= inbound24H * 0.8 && inbound24H > 500 → flag
```

**FlagReason**: `PASS_THROUGH_ACCOUNT`

Accounts acting as pass-through nodes receive fraud proceeds and immediately transfer them forward. The net balance approaches zero as funds are laundered through the account.

---

### Category TR — Transfer-Specific Rules

---

#### RULE-TR01: Transfer to Abnormal Destination Card

**Fields**: `isTransfer`, `destinationCard` (cross-referenced abnormal state)

**Logic**:
```
if isTransfer == true && destinationCard.abnormal == true → flag
```

**FlagReason**: `TRANSFER_TO_ABNORMAL_DESTINATION`

Transferring to a known-bad card is a strong indicator of deliberate money mule activity. The ingestion service's `ABNORMAL_TRANSFER` state directly covers this case.

---

#### RULE-TR02: High-Value Unauthenticated Transfer

**Fields**: `isTransfer`, `amount`, `authenticationFlag`

**Logic**:
```
if isTransfer == true && amount > 1000 && authenticationFlag == 0 → flag
if isTransfer == true && amount > 500 && authenticationFlag == null → flag
```

**FlagReason**: `UNAUTHENTICATED_LARGE_TRANSFER`

Banks require authentication for transfers above internal thresholds. An unauthenticated large transfer suggests account takeover where the fraudster bypassed 2FA/PIN, possibly through session hijacking or social engineering.

---

#### RULE-TR03: Rapid Relay Transfer

**Fields**: `isTransfer`, previous transaction's `destinationCard`, `secondsSinceLastTransaction`

**Logic**:
```
if isTransfer == true
   && previousTx.isTransfer == true
   && previousTx.destinationCard == currentTx.sourceCard
   && secondsSinceLastTransaction < 300 → flag
```

**FlagReason**: `RAPID_RELAY_TRANSFER`

The classic "authorized push payment" fraud: victim is tricked into moving funds to an "intermediate safe account," and then a second hop immediately pushes funds to the fraudster. Detecting the relay — inbound transfer followed within 5 minutes by outbound transfer from the same card — catches this pattern.

---

### Category C — Composite Rules

Individual weak signals become powerful when combined. The composite rules below fire on co-occurring signals that individually fall below threshold but jointly exceed it.

---

#### RULE-C01: Composite Fraud Score

Assign numeric weights to each triggered rule and sum them. Flag when the total exceeds a calibrated threshold.

**Suggested weight table** (calibrate against your labeled data):

| Triggered Condition | Points |
|---|---|
| `velocity1H >= 3` | 20 |
| `velocity1H >= 5` | 40 |
| `velocity24H >= 10` | 20 |
| `zScore >= 2.5` | 25 |
| `zScore >= 3.0` | 45 |
| `ratioToMedian >= 5` | 25 |
| `isNight == true` | 15 |
| `secondsSinceLastTransaction < 60` | 30 |
| `authenticationFlag == 0 && amount > 100` | 30 |
| `entryMode == 01` (keyed CNP) | 20 |
| `entryMode == 79` (chip fallback) | 40 |
| `isTransfer && authenticationFlag == 0` | 35 |
| `merchantAcquirer == null` | 25 |
| Response code was recently non-`00` | 20 |

**Logic**:
```
totalScore = sum of all triggered rule weights
if totalScore >= 60 → flag as SUSPICIOUS
if totalScore >= 100 → flag as HIGH_RISK
```

**FlagReason**: `COMPOSITE_SCORE_EXCEEDED`

This approach mirrors how Visa VASD, Mastercard Decision Intelligence, and Stripe Radar operate at their core before ML layers. No single signal is reliable enough alone; the composite eliminates the high false-positive rate of individual rules.

---

#### RULE-C02: Impossible Field Combination

**Fields**: `entryMode`, `authenticationFlag`, `isTransfer`, `destinationCard`

**Logic**:
```
# Chip transactions above contactless floor should never lack authentication
if entryMode == 05 && authenticationFlag == 0 && amount > 25 → flag

# Transfers must have destination cards
if isTransfer == true && destinationCard == null → flag

# CNP high-value with no auth
if entryMode == 01 && authenticationFlag == 0 && amount > 200 → flag
```

**FlagReason**: `IMPOSSIBLE_FIELD_COMBINATION`

Certain field value combinations are definitionally impossible under normal payment network operations. When they appear, either the data has been manipulated or a fraud bypass technique is in use.

---

#### RULE-C03: Multi-Axis Night Burst

**Fields**: `velocity1H`, `zScore`, `isNight`

**Logic**:
```
if velocity1H >= 3 && zScore >= 2.0 && isNight == true → flag
```

**FlagReason**: `MULTI_AXIS_NIGHT_BURST`

Three to four transactions in an hour is unusual but possible during daytime. A Z-score of 2.0 is notable but not definitive alone. A night transaction is slightly elevated risk alone. All three simultaneously — multiple large-for-this-card transactions in the middle of the night — is a strong joint signal.

---

## FlagReason Enum Expansion

The current placeholder `FOO` should be replaced with one value per rule:

```java
public enum FlagReason {
    // Velocity
    HIGH_VELOCITY_1H,
    HIGH_VELOCITY_24H,
    HIGH_MERCHANT_SPREAD_1H,
    RAPID_SEQUENTIAL_TRANSACTIONS,
    VELOCITY_SPIKE,

    // Amount
    AMOUNT_Z_SCORE_OUTLIER,
    AMOUNT_RATIO_TO_MEDIAN_SPIKE,
    AMOUNT_EXCEEDS_HISTORICAL_MAX,
    ROUND_NUMBER_AMOUNT,
    MICRO_CHARGE_CARD_TESTING,
    AMOUNT_BELOW_AUTH_THRESHOLD,

    // Time
    NIGHT_TRANSACTION,
    DORMANT_CARD_REACTIVATION,
    NEW_CARD_HIGH_VALUE_FIRST_TX,

    // Entry / Auth
    CARD_NOT_PRESENT,
    CHIP_FALLBACK_TO_MAGSTRIPE,
    NO_AUTH_HIGH_VALUE,
    NO_AUTH_CNP,
    UNAUTHENTICATED_TRANSFER,

    // Response codes
    DECLINED_THEN_APPROVED,
    HIGH_DECLINE_RATE,
    WRONG_PIN_SPIKE,

    // Merchant / Terminal
    HIGH_RISK_MERCHANT_CATEGORY,
    NULL_MERCHANT,
    TERMINAL_ID_MISMATCH,

    // Structuring
    STRUCTURING_SUB_THRESHOLD,
    FAN_OUT_TRANSFER,
    PASS_THROUGH_ACCOUNT,

    // Transfers
    TRANSFER_TO_ABNORMAL_DESTINATION,
    UNAUTHENTICATED_LARGE_TRANSFER,
    RAPID_RELAY_TRANSFER,

    // Composite
    COMPOSITE_SCORE_EXCEEDED,
    IMPOSSIBLE_FIELD_COMBINATION,
    MULTI_AXIS_NIGHT_BURST,
}
```

---

## Transaction Model Fields to Add

To enable the full rule set, these fields from `CreditTransactionDto` should be added to `Transaction` and included in `fromDto()`:

| Field to add | Required by rules |
|---|---|
| `sourceCard` | V05, R01–R03, TR03, S03 |
| `isTransfer` | E05, TR01–TR03, S02–S03, C02 |
| `entryMode` | E01, E02, E04, C02, C03 |
| `authenticationFlag` | E03–E05, A06, TR02, C01, C02 |
| `responseCode` | R01–R03 |
| `terminalId` | M03 |

---

## Implementation Notes

- **Rules engine location**: `ClassifyingService.java` — currently empty, this is where all rule functions live.
- **Thresholds**: Per the CLAUDE.md convention, all numeric constants (velocity thresholds, Z-score cutoffs, amount limits, etc.) must live in a single config class and be injected — not hardcoded inline.
- **Multiple triggers**: A transaction may trigger more than one rule. Store all `FlagReason` values (or use the composite score approach) rather than stopping at the first match.
- **Pre-computed fields**: `velocity1H`, `velocity24H`, `zScore`, `ratioToMedian`, `isNight`, `secondsSinceLastTransaction` are already computed by the ingestion service and available on the incoming DTO — approximately 60% of the above rules need no additional computation in this service.
- **Redis**: Rules requiring historical counts not already computed by the ingestion service (e.g., distinct merchant count in 1H, response code history) should use the Redis connection already configured in this service.