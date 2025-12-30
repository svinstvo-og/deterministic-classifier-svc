package varta.deterministic_clasifying_svc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreditTransactionDto (
        @JsonProperty("transaction_internal_id")
        Long transactionInternalId,
        @JsonProperty("transaction_pan_reference")
        String transactionPanReference,
        @JsonProperty("is_transfer")
        Boolean isTransfer,
        @JsonProperty("transaction_code")
        long transactionCode,
        @JsonProperty("system_trace_id")
        int systemTraceId,
        @JsonProperty("transaction_amount")
        BigDecimal transactionAmount,
        @JsonProperty("transaction_composite_key")
        String transactionCompositeKey,
        @JsonProperty("processed_at")
        LocalDateTime processedAt,
        @JsonProperty("response_code")
        int responseCode,
        @JsonProperty("entry_mode")
        Integer entryMode,
        @JsonProperty("transaction_description")
        String transactionDescription,
        @JsonProperty("terminal_type_code")
        Integer terminalTypeCode,
        @JsonProperty("terminal_id")
        Integer terminalId,
        @JsonProperty("authentication_flag")
        Integer authenticationFlag,
        boolean abnormal,
        @JsonProperty("abnormal_state")
        int abnormalState,
        @JsonProperty("source_card")
        Long sourceCard,
        @JsonProperty("destination_card")
        Long destinationCard,
        @JsonProperty("merchant_acquirer")
        Long merchantAcquirer,
        String op,
        Long ts_ms
) { }