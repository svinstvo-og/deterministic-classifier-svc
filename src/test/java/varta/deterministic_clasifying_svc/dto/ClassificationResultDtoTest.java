package varta.deterministic_clasifying_svc.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClassificationResultDtoTest {

    @Test
    void constructsFlaggedResult() {
        Instant now = Instant.now();
        List<FlagReason> reasons = List.of(FlagReason.HIGH_VELOCITY_1H, FlagReason.NIGHT_TRANSACTION);

        ClassificationResultDto result = new ClassificationResultDto(42L, true, reasons, now);

        assertEquals(42L, result.transactionInternalId());
        assertTrue(result.flaggedAbnormal());
        assertEquals(2, result.flagReasons().size());
        assertTrue(result.flagReasons().contains(FlagReason.HIGH_VELOCITY_1H));
        assertTrue(result.flagReasons().contains(FlagReason.NIGHT_TRANSACTION));
        assertEquals(now, result.classifiedAt());
    }

    @Test
    void constructsCleanResult() {
        Instant now = Instant.now();

        ClassificationResultDto result = new ClassificationResultDto(99L, false, List.of(), now);

        assertEquals(99L, result.transactionInternalId());
        assertFalse(result.flaggedAbnormal());
        assertTrue(result.flagReasons().isEmpty());
    }

    @Test
    void equalityBasedOnAllFields() {
        Instant now = Instant.now();
        List<FlagReason> reasons = List.of(FlagReason.ROUND_NUMBER_AMOUNT);

        ClassificationResultDto a = new ClassificationResultDto(1L, true, reasons, now);
        ClassificationResultDto b = new ClassificationResultDto(1L, true, reasons, now);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void notEqualWhenFieldsDiffer() {
        Instant now = Instant.now();

        ClassificationResultDto a = new ClassificationResultDto(1L, true, List.of(), now);
        ClassificationResultDto b = new ClassificationResultDto(2L, true, List.of(), now);

        assertNotEquals(a, b);
    }
}
