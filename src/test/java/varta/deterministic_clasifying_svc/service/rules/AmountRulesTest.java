package varta.deterministic_clasifying_svc.service.rules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
class AmountRulesTest {

    private RuleConfig ruleConfig;
    private AmountRules amountRules;

    @BeforeEach
    void setUp() {
        ruleConfig = new RuleConfig();
        ReflectionTestUtils.setField(ruleConfig, "roundNumberMin", 200);
        ReflectionTestUtils.setField(ruleConfig, "roundNumberHighMin", 500);
        ReflectionTestUtils.setField(ruleConfig, "microChargeMax", 1.00);
        ReflectionTestUtils.setField(ruleConfig, "authThresholdLowMin", 45.00);
        ReflectionTestUtils.setField(ruleConfig, "authThresholdLowMax", 49.99);
        ReflectionTestUtils.setField(ruleConfig, "authThresholdHighMin", 95.00);
        ReflectionTestUtils.setField(ruleConfig, "authThresholdHighMax", 99.99);

        amountRules = new AmountRules(ruleConfig);
    }

    private Transaction.TransactionBuilder baseTransaction() {
        return Transaction.builder()
                .transactionInternalId(1L)
                .sourceCard(100L)
                .merchantAcquirer(200L)
                .processedAt(LocalDateTime.now());
    }

    // -------------------------------------------------------------------------

    @Nested
    class RuleA01_ZScoreOutlier {

        @Test
        void stubAlwaysReturnsEmptyList() {
            Transaction tx = baseTransaction().amount(BigDecimal.valueOf(9999)).build();
            List<FlagReason> reasons = amountRules.evaluate(tx);
            assertFalse(reasons.contains(FlagReason.AMOUNT_Z_SCORE_OUTLIER));
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    class RuleA02_RatioToMedian {

        @Test
        void stubAlwaysReturnsEmptyList() {
            Transaction tx = baseTransaction().amount(BigDecimal.valueOf(9999)).build();
            List<FlagReason> reasons = amountRules.evaluate(tx);
            assertFalse(reasons.contains(FlagReason.AMOUNT_RATIO_TO_MEDIAN_SPIKE));
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    class RuleA03_HistoricalMax {

        @Test
        void stubAlwaysReturnsEmptyList() {
            Transaction tx = baseTransaction().amount(BigDecimal.valueOf(9999)).build();
            List<FlagReason> reasons = amountRules.evaluate(tx);
            assertFalse(reasons.contains(FlagReason.AMOUNT_EXCEEDS_HISTORICAL_MAX));
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    class RuleA04_RoundNumberAmount {

        @Test
        void flagsWhenDivisibleBy500AndAtHighMin() {
            Transaction tx = baseTransaction().amount(BigDecimal.valueOf(500)).build();
            List<FlagReason> reasons = amountRules.evaluate(tx);
            assertTrue(reasons.contains(FlagReason.ROUND_NUMBER_AMOUNT));
        }

        @Test
        void flagsWhenDivisibleBy500AndAboveHighMin() {
            Transaction tx = baseTransaction().amount(BigDecimal.valueOf(1000)).build();
            List<FlagReason> reasons = amountRules.evaluate(tx);
            assertTrue(reasons.contains(FlagReason.ROUND_NUMBER_AMOUNT));
        }

        @Test
        void flagsWhenDivisibleBy100AndAtMin() {
            Transaction tx = baseTransaction().amount(BigDecimal.valueOf(200)).build();
            List<FlagReason> reasons = amountRules.evaluate(tx);
            assertTrue(reasons.contains(FlagReason.ROUND_NUMBER_AMOUNT));
        }

        @Test
        void flagsWhenDivisibleBy100AndAboveMin() {
            Transaction tx = baseTransaction().amount(BigDecimal.valueOf(300)).build();
            List<FlagReason> reasons = amountRules.evaluate(tx);
            assertTrue(reasons.contains(FlagReason.ROUND_NUMBER_AMOUNT));
        }

        @Test
        void doesNotFlagWhenDivisibleBy100ButBelowMin() {
            Transaction tx = baseTransaction().amount(BigDecimal.valueOf(199)).build();
            List<FlagReason> reasons = amountRules.evaluate(tx);
            assertFalse(reasons.contains(FlagReason.ROUND_NUMBER_AMOUNT));
        }

        @Test
        void doesNotFlagWhenNotDivisibleBy100() {
            Transaction tx = baseTransaction().amount(BigDecimal.valueOf(150)).build();
            List<FlagReason> reasons = amountRules.evaluate(tx);
            assertFalse(reasons.contains(FlagReason.ROUND_NUMBER_AMOUNT));
        }

        @Test
        void doesNotFlagWhenAmountIsNull() {
            Transaction tx = baseTransaction().amount(null).build();
            List<FlagReason> reasons = amountRules.evaluate(tx);
            assertFalse(reasons.contains(FlagReason.ROUND_NUMBER_AMOUNT));
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    class RuleA05_MicroChargeCardTesting {

        @Test
        void flagsWhenAmountBelowThresholdAndHighVelocity() {
            Transaction tx = baseTransaction()
                    .amount(BigDecimal.valueOf(0.50))
                    .velocity1H(2)
                    .build();
            List<FlagReason> reasons = amountRules.evaluate(tx);
            assertTrue(reasons.contains(FlagReason.MICRO_CHARGE_CARD_TESTING));
        }

        @Test
        void flagsWhenAmountBelowThresholdAndRecentTransaction() {
            Transaction tx = baseTransaction()
                    .amount(BigDecimal.valueOf(0.50))
                    .secondsSinceLastTransaction(100L)
                    .build();
            List<FlagReason> reasons = amountRules.evaluate(tx);
            assertTrue(reasons.contains(FlagReason.MICRO_CHARGE_CARD_TESTING));
        }

        @Test
        void flagsOnceWhenBothConditionsMet() {
            Transaction tx = baseTransaction()
                    .amount(BigDecimal.valueOf(0.50))
                    .velocity1H(2)
                    .secondsSinceLastTransaction(100L)
                    .build();
            List<FlagReason> reasons = amountRules.evaluate(tx);
            assertTrue(reasons.contains(FlagReason.MICRO_CHARGE_CARD_TESTING));
            assertEquals(1, reasons.stream().filter(r -> r == FlagReason.MICRO_CHARGE_CARD_TESTING).count());
        }

        @Test
        void doesNotFlagWhenNeitherConditionMet() {
            Transaction tx = baseTransaction()
                    .amount(BigDecimal.valueOf(0.50))
                    .velocity1H(1)
                    .secondsSinceLastTransaction(400L)
                    .build();
            List<FlagReason> reasons = amountRules.evaluate(tx);
            assertFalse(reasons.contains(FlagReason.MICRO_CHARGE_CARD_TESTING));
        }

        @Test
        void doesNotFlagWhenAmountAtThreshold() {
            Transaction tx = baseTransaction()
                    .amount(BigDecimal.valueOf(1.00))
                    .velocity1H(2)
                    .secondsSinceLastTransaction(100L)
                    .build();
            List<FlagReason> reasons = amountRules.evaluate(tx);
            assertFalse(reasons.contains(FlagReason.MICRO_CHARGE_CARD_TESTING));
        }

        @Test
        void doesNotFlagWhenAmountIsNull() {
            Transaction tx = baseTransaction()
                    .amount(null)
                    .velocity1H(2)
                    .build();
            List<FlagReason> reasons = amountRules.evaluate(tx);
            assertFalse(reasons.contains(FlagReason.MICRO_CHARGE_CARD_TESTING));
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    class RuleA06_AmountBelowAuthThreshold {

        @Test
        void flagsInLowBandWithZeroAuthFlag() {
            Transaction tx = baseTransaction()
                    .amount(BigDecimal.valueOf(47.50))
                    .authenticationFlag(0)
                    .build();
            List<FlagReason> reasons = amountRules.evaluate(tx);
            assertTrue(reasons.contains(FlagReason.AMOUNT_BELOW_AUTH_THRESHOLD));
        }

        @Test
        void flagsInHighBandWithZeroAuthFlag() {
            Transaction tx = baseTransaction()
                    .amount(BigDecimal.valueOf(97.00))
                    .authenticationFlag(0)
                    .build();
            List<FlagReason> reasons = amountRules.evaluate(tx);
            assertTrue(reasons.contains(FlagReason.AMOUNT_BELOW_AUTH_THRESHOLD));
        }

        @Test
        void flagsWhenAuthFlagIsNull() {
            Transaction tx = baseTransaction()
                    .amount(BigDecimal.valueOf(47.50))
                    .authenticationFlag(null)
                    .build();
            List<FlagReason> reasons = amountRules.evaluate(tx);
            assertTrue(reasons.contains(FlagReason.AMOUNT_BELOW_AUTH_THRESHOLD));
        }

        @Test
        void doesNotFlagWhenAuthenticated() {
            Transaction tx = baseTransaction()
                    .amount(BigDecimal.valueOf(47.50))
                    .authenticationFlag(1)
                    .build();
            List<FlagReason> reasons = amountRules.evaluate(tx);
            assertFalse(reasons.contains(FlagReason.AMOUNT_BELOW_AUTH_THRESHOLD));
        }

        @Test
        void doesNotFlagWhenAboveLowBand() {
            Transaction tx = baseTransaction()
                    .amount(BigDecimal.valueOf(50.00))
                    .authenticationFlag(0)
                    .build();
            List<FlagReason> reasons = amountRules.evaluate(tx);
            assertFalse(reasons.contains(FlagReason.AMOUNT_BELOW_AUTH_THRESHOLD));
        }

        @Test
        void doesNotFlagWhenBelowLowBand() {
            Transaction tx = baseTransaction()
                    .amount(BigDecimal.valueOf(44.99))
                    .authenticationFlag(0)
                    .build();
            List<FlagReason> reasons = amountRules.evaluate(tx);
            assertFalse(reasons.contains(FlagReason.AMOUNT_BELOW_AUTH_THRESHOLD));
        }
    }
}
