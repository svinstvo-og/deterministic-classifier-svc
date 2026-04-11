package varta.deterministic_clasifying_svc.service;

import org.junit.jupiter.api.Test;
import varta.deterministic_clasifying_svc.dto.FlagReason;
import varta.deterministic_clasifying_svc.model.Transaction;
import varta.deterministic_clasifying_svc.service.rules.Rule;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClassifyingServiceTest {

    private Transaction baseTransaction() {
        return Transaction.builder()
                .transactionInternalId(1L)
                .sourceCard(100L)
                .amount(BigDecimal.TEN)
                .processedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void aggregatesResultsFromMultipleRules() {
        Rule rule1 = tx -> List.of(FlagReason.HIGH_VELOCITY_1H);
        Rule rule2 = tx -> List.of(FlagReason.HIGH_VELOCITY_24H);
        ClassifyingService service = new ClassifyingService(List.of(rule1, rule2));

        List<FlagReason> reasons = service.classify(baseTransaction());

        assertTrue(reasons.contains(FlagReason.HIGH_VELOCITY_1H));
        assertTrue(reasons.contains(FlagReason.HIGH_VELOCITY_24H));
    }

    @Test
    void setsFlaggedAbnormalAndFlagReasonsWhenAnyRuleFires() {
        Rule rule = tx -> List.of(FlagReason.HIGH_VELOCITY_1H);
        ClassifyingService service = new ClassifyingService(List.of(rule));
        Transaction tx = baseTransaction();

        service.classify(tx);

        assertTrue(tx.getFlaggedAbnormal());
        assertNotNull(tx.getFlagReasons());
        assertFalse(tx.getFlagReasons().isEmpty());
    }

    @Test
    void doesNotSetFlaggedAbnormalWhenNoRuleFires() {
        Rule rule = tx -> List.of();
        ClassifyingService service = new ClassifyingService(List.of(rule));
        Transaction tx = baseTransaction();

        service.classify(tx);

        assertNull(tx.getFlaggedAbnormal());
        assertNull(tx.getFlagReasons());
    }

    @Test
    void returnsEmptyListWhenNoRulesConfigured() {
        ClassifyingService service = new ClassifyingService(List.of());
        Transaction tx = baseTransaction();

        List<FlagReason> reasons = service.classify(tx);

        assertTrue(reasons.isEmpty());
        assertNull(tx.getFlaggedAbnormal());
    }
}
