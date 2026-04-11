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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
// LENIENT: stubRedisNoOp() pre-stubs repository calls; some tests don't reach all stubs.
@MockitoSettings(strictness = Strictness.LENIENT)
class StructuringRulesTest {

    @Mock
    private VelocityRepository velocityRepository;

    private RuleConfig ruleConfig;
    private StructuringRules structuringRules;

    @BeforeEach
    void setUp() {
        ruleConfig = new RuleConfig();
        ReflectionTestUtils.setField(ruleConfig, "fanOutDistinctDestinations", 3);
        ReflectionTestUtils.setField(ruleConfig, "fanOutTotalThreshold", 1000.0);

        structuringRules = new StructuringRules(ruleConfig, velocityRepository);
    }

    private Transaction.TransactionBuilder baseTransfer() {
        return Transaction.builder()
                .isTransfer(true)
                .sourceCard(1001L)
                .destinationCard(2001L)
                .transactionInternalId(9999L)
                .amount(BigDecimal.valueOf(200));
    }

    private void stubRedisNoOp() {
        when(velocityRepository.getDistinctDestinationCount1H(any())).thenReturn(0L);
        when(velocityRepository.getTotalTransferred1H(any())).thenReturn(BigDecimal.ZERO);
    }

    // -------------------------------------------------------------------------

    @Nested
    class RuleS01_SubThresholdStructuring {

        @Test
        void returnsEmptyListRegardlessOfInput() {
            stubRedisNoOp();
            Transaction tx = baseTransfer().amount(BigDecimal.valueOf(9500)).build();

            List<FlagReason> reasons = structuringRules.evaluate(tx);

            assertFalse(reasons.contains(FlagReason.STRUCTURING_SUB_THRESHOLD));
        }

        @Test
        void returnsEmptyListForNonTransfer() {
            stubRedisNoOp();
            Transaction tx = Transaction.builder()
                    .isTransfer(false)
                    .sourceCard(1001L)
                    .amount(BigDecimal.valueOf(9500))
                    .build();

            List<FlagReason> reasons = structuringRules.evaluate(tx);

            assertFalse(reasons.contains(FlagReason.STRUCTURING_SUB_THRESHOLD));
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    class RuleS02_FanOutTransfer {

        @Test
        void doesNotFlagWhenIsTransferIsFalse() {
            Transaction tx = baseTransfer().isTransfer(false).build();

            List<FlagReason> reasons = structuringRules.evaluate(tx);

            assertFalse(reasons.contains(FlagReason.FAN_OUT_TRANSFER));
            verify(velocityRepository, never()).getDistinctDestinationCount1H(any());
        }

        @Test
        void doesNotFlagWhenIsTransferIsNull() {
            Transaction tx = baseTransfer().isTransfer(null).build();

            List<FlagReason> reasons = structuringRules.evaluate(tx);

            assertFalse(reasons.contains(FlagReason.FAN_OUT_TRANSFER));
            verify(velocityRepository, never()).getDistinctDestinationCount1H(any());
        }

        @Test
        void doesNotFlagWhenSourceCardIsNull() {
            Transaction tx = baseTransfer().sourceCard(null).build();

            List<FlagReason> reasons = structuringRules.evaluate(tx);

            assertFalse(reasons.contains(FlagReason.FAN_OUT_TRANSFER));
            verify(velocityRepository, never()).getDistinctDestinationCount1H(any());
        }

        @Test
        void flagsWhenDistinctDestAtThreshold() {
            when(velocityRepository.getDistinctDestinationCount1H(1001L)).thenReturn(3L);
            when(velocityRepository.getTotalTransferred1H(1001L)).thenReturn(BigDecimal.valueOf(500));
            Transaction tx = baseTransfer().build();

            List<FlagReason> reasons = structuringRules.evaluate(tx);

            assertTrue(reasons.contains(FlagReason.FAN_OUT_TRANSFER));
        }

        @Test
        void flagsWhenDistinctDestAboveThreshold() {
            when(velocityRepository.getDistinctDestinationCount1H(1001L)).thenReturn(5L);
            when(velocityRepository.getTotalTransferred1H(1001L)).thenReturn(BigDecimal.valueOf(100));
            Transaction tx = baseTransfer().build();

            List<FlagReason> reasons = structuringRules.evaluate(tx);

            assertTrue(reasons.contains(FlagReason.FAN_OUT_TRANSFER));
        }

        @Test
        void flagsWhenTotalExceedsThresholdAndDistinctDestIsTwo() {
            when(velocityRepository.getDistinctDestinationCount1H(1001L)).thenReturn(2L);
            when(velocityRepository.getTotalTransferred1H(1001L)).thenReturn(BigDecimal.valueOf(1001));
            Transaction tx = baseTransfer().build();

            List<FlagReason> reasons = structuringRules.evaluate(tx);

            assertTrue(reasons.contains(FlagReason.FAN_OUT_TRANSFER));
        }

        @Test
        void doesNotFlagWhenDistinctDestIsOneEvenWithHighTotal() {
            when(velocityRepository.getDistinctDestinationCount1H(1001L)).thenReturn(1L);
            when(velocityRepository.getTotalTransferred1H(1001L)).thenReturn(BigDecimal.valueOf(5000));
            Transaction tx = baseTransfer().build();

            List<FlagReason> reasons = structuringRules.evaluate(tx);

            assertFalse(reasons.contains(FlagReason.FAN_OUT_TRANSFER));
        }

        @Test
        void doesNotFlagWhenTotalAtThresholdAndDistinctDestIsTwo() {
            when(velocityRepository.getDistinctDestinationCount1H(1001L)).thenReturn(2L);
            when(velocityRepository.getTotalTransferred1H(1001L)).thenReturn(BigDecimal.valueOf(1000));
            Transaction tx = baseTransfer().build();

            List<FlagReason> reasons = structuringRules.evaluate(tx);

            assertFalse(reasons.contains(FlagReason.FAN_OUT_TRANSFER));
        }

        @Test
        void doesNotAddDuplicateFlagWhenBothConditionsAreTrue() {
            // distinctDest >= 3 (first condition) AND total > 1000 with distinctDest >= 2 (second condition)
            // early-return after first condition means only one FAN_OUT_TRANSFER added
            when(velocityRepository.getDistinctDestinationCount1H(1001L)).thenReturn(3L);
            when(velocityRepository.getTotalTransferred1H(1001L)).thenReturn(BigDecimal.valueOf(2000));
            Transaction tx = baseTransfer().build();

            List<FlagReason> reasons = structuringRules.evaluate(tx);

            assertEquals(1, reasons.stream().filter(r -> r == FlagReason.FAN_OUT_TRANSFER).count());
        }

        @Test
        void returnsOnlyFanOutTransferReason() {
            when(velocityRepository.getDistinctDestinationCount1H(1001L)).thenReturn(3L);
            when(velocityRepository.getTotalTransferred1H(1001L)).thenReturn(BigDecimal.valueOf(500));
            Transaction tx = baseTransfer().build();

            List<FlagReason> reasons = structuringRules.evaluate(tx);

            assertEquals(List.of(FlagReason.FAN_OUT_TRANSFER), reasons);
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    class RuleS03_PassThroughAccount {

        @Test
        void returnsEmptyListRegardlessOfInput() {
            stubRedisNoOp();
            Transaction tx = baseTransfer().build();

            List<FlagReason> reasons = structuringRules.evaluate(tx);

            assertFalse(reasons.contains(FlagReason.PASS_THROUGH_ACCOUNT));
        }

        @Test
        void returnsEmptyListForNonTransfer() {
            stubRedisNoOp();
            Transaction tx = Transaction.builder()
                    .isTransfer(false)
                    .sourceCard(1001L)
                    .amount(BigDecimal.valueOf(600))
                    .build();

            List<FlagReason> reasons = structuringRules.evaluate(tx);

            assertFalse(reasons.contains(FlagReason.PASS_THROUGH_ACCOUNT));
        }
    }
}
