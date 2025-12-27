package varta.deterministic_clasifying_svc.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreditTransactionDto (
        Long transactionInternalId,
        String transactionPanReference,
        Boolean isTransfer,
        long transactionCode,
        int systemTraceId,
        BigDecimal transactionAmount,
        String transactionCompositeKey,
        LocalDateTime processedAt,
        int responseCode,
        Integer entryMode,
        String transactionDescription,
        Integer terminalTypeCode,
        Integer terminalId,
        Integer authenticationFlag,
        boolean abnormal,
        int abnormalState,
        Long sourceCard,
        Long destinationCard,
        Long merchantAcquirer
) { }