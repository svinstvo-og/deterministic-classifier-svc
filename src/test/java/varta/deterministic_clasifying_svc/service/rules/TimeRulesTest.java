package varta.deterministic_clasifying_svc.service.rules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import varta.deterministic_clasifying_svc.config.RuleConfig;
import varta.deterministic_clasifying_svc.dto.FlagReason;
import varta.deterministic_clasifying_svc.model.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TimeRulesTest {

    private RuleConfig ruleConfig;
    private TimeRules timeRules;

    @BeforeEach
    void setUp() {
        ruleConfig = new RuleConfig();
        ReflectionTestUtils.setField(ruleConfig, "nightStartHour", 1);
        ReflectionTestUtils.setField(ruleConfig, "nightEndHour", 5);
        ReflectionTestUtils.setField(ruleConfig, "rapidSequentialHighSeconds", 30L);
        ReflectionTestUtils.setField(ruleConfig, "rapidSequentialModerateSeconds", 120L);
        ReflectionTestUtils.setField(ruleConfig, "dormantDaysHigh", 90L);
        ReflectionTestUtils.setField(ruleConfig, "dormantAmountThreshold", 300);
        ReflectionTestUtils.setField(ruleConfig, "dormantDaysModerate", 180L);

        timeRules = new TimeRules(ruleConfig);
    }

    private Transaction.TransactionBuilder baseTransaction() {
        return Transaction.builder()
                .transactionInternalId(1L)
                .sourceCard(100L)
                .merchantAcquirer(200L)
                .processedAt(LocalDateTime.of(2024, 6, 15, 12, 0));
    }

    // -------------------------------------------------------------------------

    @Nested
    class RuleT01_NightTransaction {

        @Test
        void flagsWhenIsNightTrue() {
            Transaction tx = baseTransaction().isNight(true).build();
            List<FlagReason> reasons = timeRules.evaluate(tx);
            assertTrue(reasons.contains(FlagReason.NIGHT_TRANSACTION));
        }

        @Test
        void doesNotFlagWhenIsNightFalse() {
            Transaction tx = baseTransaction().isNight(false).build();
            List<FlagReason> reasons = timeRules.evaluate(tx);
            assertFalse(reasons.contains(FlagReason.NIGHT_TRANSACTION));
        }

        @Test
        void flagsViaFallbackWhenIsNightNullAndHourInRange() {
            Transaction tx = baseTransaction()
                    .isNight(null)
                    .processedAt(LocalDateTime.of(2024, 6, 15, 3, 0))
                    .build();
            List<FlagReason> reasons = timeRules.evaluate(tx);
            assertTrue(reasons.contains(FlagReason.NIGHT_TRANSACTION));
        }

        @Test
        void doesNotFlagViaFallbackWhenIsNightNullAndHourOutOfRange() {
            Transaction tx = baseTransaction()
                    .isNight(null)
                    .processedAt(LocalDateTime.of(2024, 6, 15, 10, 0))
                    .build();
            List<FlagReason> reasons = timeRules.evaluate(tx);
            assertFalse(reasons.contains(FlagReason.NIGHT_TRANSACTION));
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    class RuleT02_RapidSequential {

        @Test
        void flagsWhenSecondsBelowHighThreshold() {
            Transaction tx = baseTransaction().secondsSinceLastTransaction(20L).build();
            List<FlagReason> reasons = timeRules.evaluate(tx);
            assertTrue(reasons.contains(FlagReason.RAPID_SEQUENTIAL_TRANSACTIONS));
        }

        @Test
        void flagsWhenSecondsBetweenHighAndModerateThreshold() {
            Transaction tx = baseTransaction().secondsSinceLastTransaction(60L).build();
            List<FlagReason> reasons = timeRules.evaluate(tx);
            assertTrue(reasons.contains(FlagReason.RAPID_SEQUENTIAL_TRANSACTIONS));
        }

        @Test
        void doesNotFlagWhenSecondsAtOrAboveModerateThreshold() {
            Transaction tx = baseTransaction().secondsSinceLastTransaction(120L).build();
            List<FlagReason> reasons = timeRules.evaluate(tx);
            assertFalse(reasons.contains(FlagReason.RAPID_SEQUENTIAL_TRANSACTIONS));
        }

        @Test
        void doesNotFlagWhenSecondsIsNull() {
            Transaction tx = baseTransaction().secondsSinceLastTransaction(null).build();
            List<FlagReason> reasons = timeRules.evaluate(tx);
            assertFalse(reasons.contains(FlagReason.RAPID_SEQUENTIAL_TRANSACTIONS));
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    class RuleT03_DormantCardReactivation {

        private static final long DAYS_91 = 91L * 86400;
        private static final long DAYS_181 = 181L * 86400;
        private static final long DAYS_45 = 45L * 86400;

        @Test
        void flagsWhenDaysAboveHighAndAmountAboveThreshold() {
            Transaction tx = baseTransaction()
                    .secondsSinceLastTransaction(DAYS_91)
                    .amount(BigDecimal.valueOf(350))
                    .build();
            List<FlagReason> reasons = timeRules.evaluate(tx);
            assertTrue(reasons.contains(FlagReason.DORMANT_CARD_REACTIVATION));
        }

        @Test
        void doesNotFlagWhenDaysAboveHighButAmountBelowThreshold() {
            Transaction tx = baseTransaction()
                    .secondsSinceLastTransaction(DAYS_91)
                    .amount(BigDecimal.valueOf(300))
                    .build();
            List<FlagReason> reasons = timeRules.evaluate(tx);
            assertFalse(reasons.contains(FlagReason.DORMANT_CARD_REACTIVATION));
        }

        @Test
        void flagsWhenDaysAboveModerateRegardlessOfAmount() {
            Transaction tx = baseTransaction()
                    .secondsSinceLastTransaction(DAYS_181)
                    .amount(BigDecimal.valueOf(10))
                    .build();
            List<FlagReason> reasons = timeRules.evaluate(tx);
            assertTrue(reasons.contains(FlagReason.DORMANT_CARD_REACTIVATION));
        }

        @Test
        void doesNotFlagWhenDaysBelowHighThreshold() {
            Transaction tx = baseTransaction()
                    .secondsSinceLastTransaction(DAYS_45)
                    .amount(BigDecimal.valueOf(500))
                    .build();
            List<FlagReason> reasons = timeRules.evaluate(tx);
            assertFalse(reasons.contains(FlagReason.DORMANT_CARD_REACTIVATION));
        }

        @Test
        void doesNotFlagWhenSecondsIsNull() {
            Transaction tx = baseTransaction()
                    .secondsSinceLastTransaction(null)
                    .amount(BigDecimal.valueOf(500))
                    .build();
            List<FlagReason> reasons = timeRules.evaluate(tx);
            assertFalse(reasons.contains(FlagReason.DORMANT_CARD_REACTIVATION));
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    class RuleT04_NewCardHighValue {

        @Test
        void stubNeverReturnsNewCardHighValueFirstTx() {
            Transaction tx = baseTransaction()
                    .amount(BigDecimal.valueOf(9999))
                    .isTransfer(true)
                    .build();
            List<FlagReason> reasons = timeRules.evaluate(tx);
            assertFalse(reasons.contains(FlagReason.NEW_CARD_HIGH_VALUE_FIRST_TX));
        }
    }
}
