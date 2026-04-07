package varta.deterministic_clasifying_svc.model;

import org.junit.jupiter.api.Test;
import varta.deterministic_clasifying_svc.dto.CreditTransactionDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {

    private CreditTransactionDto dto(Long id, BigDecimal amount, LocalDateTime processedAt,
                                     boolean abnormal, Long destinationCard, Long merchantAcquirer) {
        return new CreditTransactionDto(
                id, "PAN-REF", false, 100L, 1, amount,
                "COMP-KEY", processedAt, 0, 1, "desc",
                1, 1, 0, abnormal, 0, 1L, destinationCard, merchantAcquirer, "c", 1000L
        );
    }

    @Test
    void fromDtoMapsAllFields() {
        LocalDateTime processedAt = LocalDateTime.of(2024, 3, 10, 14, 30);
        CreditTransactionDto creditTransactionDto =
                dto(99L, new BigDecimal("250.00"), processedAt, true, 55L, 77L);

        Transaction transaction = Transaction.fromDto(creditTransactionDto);

        assertEquals(99L, transaction.getTransactionInternalId());
        assertEquals(new BigDecimal("250.00"), transaction.getAmount());
        assertEquals(processedAt, transaction.getProcessedAt());
        assertTrue(transaction.getAbnormal());
        assertEquals(55L, transaction.getDestinationCard());
        assertEquals(77L, transaction.getMerchantAcquirer());
    }

    @Test
    void fromDtoMapsTransactionAmountToAmount() {
        BigDecimal expected = new BigDecimal("123.45");
        CreditTransactionDto creditTransactionDto =
                dto(1L, expected, LocalDateTime.now(), false, 1L, 1L);

        Transaction transaction = Transaction.fromDto(creditTransactionDto);

        // CreditTransactionDto.transactionAmount maps to Transaction.amount
        assertEquals(expected, transaction.getAmount());
    }

    @Test
    void fromDtoLeavesFlaggedAbnormalAndReasonNull() {
        // fromDto() does not set flaggedAbnormal or flagReason — the classifier sets those later
        CreditTransactionDto creditTransactionDto =
                dto(1L, BigDecimal.ONE, LocalDateTime.now(), false, 1L, 1L);

        Transaction transaction = Transaction.fromDto(creditTransactionDto);

        assertNull(transaction.getFlaggedAbnormal());
        assertNull(transaction.getFlagReason());
    }
}
