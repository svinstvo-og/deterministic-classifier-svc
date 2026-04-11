package varta.deterministic_clasifying_svc.service.rules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import varta.deterministic_clasifying_svc.config.RuleConfig;
import varta.deterministic_clasifying_svc.dto.FlagReason;
import varta.deterministic_clasifying_svc.model.Transaction;
import varta.deterministic_clasifying_svc.repository.VelocityRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
// LENIENT: stubRedisNoOp() pre-stubs both Redis calls, but when velocity1H is null
// V05 returns early and getTransactionCount30D is never invoked — that's intentional.
@MockitoSettings(strictness = Strictness.LENIENT)
class VelocityRulesTest {

    @Mock
    private VelocityRepository velocityRepository;

    private RuleConfig ruleConfig;
    private VelocityRules velocityRules;

    @BeforeEach
    void setUp() {
        ruleConfig = new RuleConfig();
        ReflectionTestUtils.setField(ruleConfig, "highVelocity1HThreshold", 5);
        ReflectionTestUtils.setField(ruleConfig, "highVelocity24HThreshold", 15);
        ReflectionTestUtils.setField(ruleConfig, "highMerchantSpread1HThreshold", 4);
        ReflectionTestUtils.setField(ruleConfig, "rapidSequentialSeconds", 60L);
        ReflectionTestUtils.setField(ruleConfig, "rapidSequentialMinVelocity", 2);
        ReflectionTestUtils.setField(ruleConfig, "velocitySpikeMultiplier", 10.0);
        ReflectionTestUtils.setField(ruleConfig, "velocitySpikeMin30DTransactions", 10L);

        velocityRules = new VelocityRules(ruleConfig, velocityRepository);
    }

    // --- Shared builder helpers ---

    private Transaction.TransactionBuilder baseTransaction() {
        return Transaction.builder()
                .transactionInternalId(1L)
                .sourceCard(100L)
                .merchantAcquirer(200L)
                .amount(BigDecimal.TEN)
                .processedAt(LocalDateTime.now());
    }

    // Silence Redis calls for rules that don't use them in a given test
    private void stubRedisNoOp() {
        when(velocityRepository.getDistinctMerchantCount1H(any())).thenReturn(0L);
        when(velocityRepository.getTransactionCount30D(any())).thenReturn(0L);
    }

    // -------------------------------------------------------------------------

    @Nested
    class RuleV01_HighVelocity1H {

        @Test
        void flagsWhenVelocity1HAtThreshold() {
            stubRedisNoOp();
            Transaction tx = baseTransaction().velocity1H(5).velocity24H(1).build();

            List<FlagReason> reasons = velocityRules.evaluate(tx);

            assertTrue(reasons.contains(FlagReason.HIGH_VELOCITY_1H));
        }

        @Test
        void flagsWhenVelocity1HAboveThreshold() {
            stubRedisNoOp();
            Transaction tx = baseTransaction().velocity1H(20).velocity24H(1).build();

            List<FlagReason> reasons = velocityRules.evaluate(tx);

            assertTrue(reasons.contains(FlagReason.HIGH_VELOCITY_1H));
        }

        @Test
        void doesNotFlagBelowThreshold() {
            stubRedisNoOp();
            Transaction tx = baseTransaction().velocity1H(4).velocity24H(1).build();

            List<FlagReason> reasons = velocityRules.evaluate(tx);

            assertFalse(reasons.contains(FlagReason.HIGH_VELOCITY_1H));
        }

        @Test
        void doesNotFlagWhenVelocity1HIsNull() {
            stubRedisNoOp();
            Transaction tx = baseTransaction().velocity1H(null).build();

            List<FlagReason> reasons = velocityRules.evaluate(tx);

            assertFalse(reasons.contains(FlagReason.HIGH_VELOCITY_1H));
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    class RuleV02_HighVelocity24H {

        @Test
        void flagsWhenVelocity24HAtThreshold() {
            stubRedisNoOp();
            Transaction tx = baseTransaction().velocity1H(1).velocity24H(15).build();

            List<FlagReason> reasons = velocityRules.evaluate(tx);

            assertTrue(reasons.contains(FlagReason.HIGH_VELOCITY_24H));
        }

        @Test
        void flagsWhenVelocity24HAboveThreshold() {
            stubRedisNoOp();
            Transaction tx = baseTransaction().velocity1H(1).velocity24H(30).build();

            List<FlagReason> reasons = velocityRules.evaluate(tx);

            assertTrue(reasons.contains(FlagReason.HIGH_VELOCITY_24H));
        }

        @Test
        void doesNotFlagBelowThreshold() {
            stubRedisNoOp();
            Transaction tx = baseTransaction().velocity1H(1).velocity24H(14).build();

            List<FlagReason> reasons = velocityRules.evaluate(tx);

            assertFalse(reasons.contains(FlagReason.HIGH_VELOCITY_24H));
        }

        @Test
        void doesNotFlagWhenVelocity24HIsNull() {
            stubRedisNoOp();
            Transaction tx = baseTransaction().velocity24H(null).build();

            List<FlagReason> reasons = velocityRules.evaluate(tx);

            assertFalse(reasons.contains(FlagReason.HIGH_VELOCITY_24H));
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    class RuleV03_HighMerchantSpread1H {

        @Test
        void flagsWhenDistinctMerchantsAtThreshold() {
            when(velocityRepository.getDistinctMerchantCount1H(100L)).thenReturn(4L);
            when(velocityRepository.getTransactionCount30D(any())).thenReturn(0L);
            Transaction tx = baseTransaction().velocity1H(1).velocity24H(1).build();

            List<FlagReason> reasons = velocityRules.evaluate(tx);

            assertTrue(reasons.contains(FlagReason.HIGH_MERCHANT_SPREAD_1H));
        }

        @Test
        void flagsWhenDistinctMerchantsAboveThreshold() {
            when(velocityRepository.getDistinctMerchantCount1H(100L)).thenReturn(10L);
            when(velocityRepository.getTransactionCount30D(any())).thenReturn(0L);
            Transaction tx = baseTransaction().velocity1H(1).velocity24H(1).build();

            List<FlagReason> reasons = velocityRules.evaluate(tx);

            assertTrue(reasons.contains(FlagReason.HIGH_MERCHANT_SPREAD_1H));
        }

        @Test
        void doesNotFlagBelowThreshold() {
            when(velocityRepository.getDistinctMerchantCount1H(100L)).thenReturn(3L);
            when(velocityRepository.getTransactionCount30D(any())).thenReturn(0L);
            Transaction tx = baseTransaction().velocity1H(1).velocity24H(1).build();

            List<FlagReason> reasons = velocityRules.evaluate(tx);

            assertFalse(reasons.contains(FlagReason.HIGH_MERCHANT_SPREAD_1H));
        }

        @Test
        void doesNotFlagWhenSourceCardIsNull() {
            // velocityRepository must not be called for merchant spread if sourceCard is null
            Transaction tx = baseTransaction().sourceCard(null).velocity1H(1).velocity24H(1).build();

            // Only tx count stub needed (V05 also checks sourceCard, so no getDistinctMerchantCount1H call)
            List<FlagReason> reasons = velocityRules.evaluate(tx);

            assertFalse(reasons.contains(FlagReason.HIGH_MERCHANT_SPREAD_1H));
            verify(velocityRepository, never()).getDistinctMerchantCount1H(any());
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    class RuleV04_RapidSequential {

        @Test
        void flagsWhenBothConditionsMet() {
            stubRedisNoOp();
            Transaction tx = baseTransaction()
                    .velocity1H(2)
                    .velocity24H(1)
                    .secondsSinceLastTransaction(30L)  // < 60
                    .build();

            List<FlagReason> reasons = velocityRules.evaluate(tx);

            assertTrue(reasons.contains(FlagReason.RAPID_SEQUENTIAL_TRANSACTIONS));
        }

        @Test
        void doesNotFlagWhenIntervalIsAtThreshold() {
            stubRedisNoOp();
            Transaction tx = baseTransaction()
                    .velocity1H(2)
                    .velocity24H(1)
                    .secondsSinceLastTransaction(60L)  // not < 60
                    .build();

            List<FlagReason> reasons = velocityRules.evaluate(tx);

            assertFalse(reasons.contains(FlagReason.RAPID_SEQUENTIAL_TRANSACTIONS));
        }

        @Test
        void doesNotFlagWhenVelocityBelowMinimum() {
            stubRedisNoOp();
            Transaction tx = baseTransaction()
                    .velocity1H(1)  // < 2
                    .velocity24H(1)
                    .secondsSinceLastTransaction(10L)
                    .build();

            List<FlagReason> reasons = velocityRules.evaluate(tx);

            assertFalse(reasons.contains(FlagReason.RAPID_SEQUENTIAL_TRANSACTIONS));
        }

        @Test
        void doesNotFlagWhenSecondsIsNull() {
            stubRedisNoOp();
            Transaction tx = baseTransaction()
                    .velocity1H(3)
                    .secondsSinceLastTransaction(null)
                    .build();

            List<FlagReason> reasons = velocityRules.evaluate(tx);

            assertFalse(reasons.contains(FlagReason.RAPID_SEQUENTIAL_TRANSACTIONS));
        }

        @Test
        void doesNotFlagWhenVelocityIsNull() {
            stubRedisNoOp();
            Transaction tx = baseTransaction()
                    .velocity1H(null)
                    .secondsSinceLastTransaction(10L)
                    .build();

            List<FlagReason> reasons = velocityRules.evaluate(tx);

            assertFalse(reasons.contains(FlagReason.RAPID_SEQUENTIAL_TRANSACTIONS));
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    class RuleV05_VelocitySpike {

        // 30D avg = 720 tx / 720h = 1.0 tx/h; spike threshold = 1.0 * 10 = 10
        private static final long COUNT_30D = 720L;

        @Test
        void flagsWhenVelocity1HExceedsSpikeThreshold() {
            when(velocityRepository.getDistinctMerchantCount1H(any())).thenReturn(0L);
            when(velocityRepository.getTransactionCount30D(100L)).thenReturn(COUNT_30D);
            Transaction tx = baseTransaction()
                    .velocity1H(11)  // 11 > 1.0 * 10 = 10
                    .velocity24H(1)
                    .build();

            List<FlagReason> reasons = velocityRules.evaluate(tx);

            assertTrue(reasons.contains(FlagReason.VELOCITY_SPIKE));
        }

        @Test
        void doesNotFlagWhenVelocity1HAtSpikeThreshold() {
            when(velocityRepository.getDistinctMerchantCount1H(any())).thenReturn(0L);
            when(velocityRepository.getTransactionCount30D(100L)).thenReturn(COUNT_30D);
            Transaction tx = baseTransaction()
                    .velocity1H(10)  // 10 is not > 10 (must be strictly greater)
                    .velocity24H(1)
                    .build();

            List<FlagReason> reasons = velocityRules.evaluate(tx);

            assertFalse(reasons.contains(FlagReason.VELOCITY_SPIKE));
        }

        @Test
        void doesNotFlagWhenHistoryBelowMinimum() {
            stubRedisNoOp();
            // count30D = 0 < min threshold of 10 — no baseline, rule skipped
            Transaction tx = baseTransaction().velocity1H(50).velocity24H(1).build();

            List<FlagReason> reasons = velocityRules.evaluate(tx);

            assertFalse(reasons.contains(FlagReason.VELOCITY_SPIKE));
        }

        @Test
        void doesNotFlagWhenSourceCardIsNull() {
            Transaction tx = baseTransaction()
                    .sourceCard(null)
                    .velocity1H(50)
                    .velocity24H(1)
                    .build();

            List<FlagReason> reasons = velocityRules.evaluate(tx);

            assertFalse(reasons.contains(FlagReason.VELOCITY_SPIKE));
        }

        @Test
        void doesNotFlagWhenVelocity1HIsNull() {
            when(velocityRepository.getDistinctMerchantCount1H(any())).thenReturn(0L);
            // getTransactionCount30D should not be reached if velocity1H is null
            Transaction tx = baseTransaction().velocity1H(null).velocity24H(1).build();

            List<FlagReason> reasons = velocityRules.evaluate(tx);

            assertFalse(reasons.contains(FlagReason.VELOCITY_SPIKE));
            verify(velocityRepository, never()).getTransactionCount30D(any());
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    class MultiRuleTriggers {

        @Test
        void multipleRulesCanTriggerSimultaneously() {
            when(velocityRepository.getDistinctMerchantCount1H(100L)).thenReturn(5L);
            when(velocityRepository.getTransactionCount30D(100L)).thenReturn(720L);
            Transaction tx = baseTransaction()
                    .velocity1H(11)   // V01 (>= 5) + V04 (>= 2) + V05 (> 10)
                    .velocity24H(20)  // V02 (>= 15)
                    .secondsSinceLastTransaction(10L)  // V04 (< 60)
                    .build();

            List<FlagReason> reasons = velocityRules.evaluate(tx);

            assertTrue(reasons.contains(FlagReason.HIGH_VELOCITY_1H));
            assertTrue(reasons.contains(FlagReason.HIGH_VELOCITY_24H));
            assertTrue(reasons.contains(FlagReason.HIGH_MERCHANT_SPREAD_1H));
            assertTrue(reasons.contains(FlagReason.RAPID_SEQUENTIAL_TRANSACTIONS));
            assertTrue(reasons.contains(FlagReason.VELOCITY_SPIKE));
        }
    }
}
