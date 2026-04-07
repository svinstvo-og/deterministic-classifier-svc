package varta.deterministic_clasifying_svc;

import org.junit.jupiter.api.Test;
import varta.deterministic_clasifying_svc.config.ObjectMapperConfig;
import varta.deterministic_clasifying_svc.dto.CreditTransactionDto;
import varta.deterministic_clasifying_svc.util.CreditTransactionMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TransactionMapperTest {

    private final CreditTransactionMapper mapper =
            new CreditTransactionMapper(new ObjectMapperConfig().objectMapper());

    @Test
    void mapsCorrectly() {
        String json = """
                {
                  "payload": {
                    "after": {
                      "transaction_internal_id": 123,
                      "transaction_pan_reference": "ref",
                      "is_transfer": true
                    }
                  }
                }
                """;
        CreditTransactionDto dto = mapper.fromDebezium(json);
        assertNotNull(dto);
        assertEquals(123L, dto.transactionInternalId());
    }

    @Test
    void mapsAllFields() {
        String json = """
                {
                  "payload": {
                    "after": {
                      "transaction_internal_id": 42,
                      "transaction_pan_reference": "PAN-REF-001",
                      "is_transfer": true,
                      "transaction_code": 100,
                      "system_trace_id": 7,
                      "transaction_amount": "199.99",
                      "transaction_composite_key": "COMP-KEY",
                      "response_code": 0,
                      "entry_mode": 5,
                      "transaction_description": "Test purchase",
                      "terminal_type_code": 3,
                      "terminal_id": 9,
                      "authentication_flag": 1,
                      "abnormal": true,
                      "abnormal_state": 2,
                      "source_card": 10,
                      "destination_card": 20,
                      "merchant_acquirer": 30,
                      "op": "c",
                      "ts_ms": 1700000000000
                    }
                  }
                }
                """;
        CreditTransactionDto dto = mapper.fromDebezium(json);

        assertEquals(42L, dto.transactionInternalId());
        assertEquals("PAN-REF-001", dto.transactionPanReference());
        assertTrue(dto.isTransfer());
        assertEquals(100L, dto.transactionCode());
        assertEquals(7, dto.systemTraceId());
        assertEquals(new BigDecimal("199.99"), dto.transactionAmount());
        assertEquals("COMP-KEY", dto.transactionCompositeKey());
        assertEquals(0, dto.responseCode());
        assertEquals(5, dto.entryMode());
        assertEquals("Test purchase", dto.transactionDescription());
        assertEquals(3, dto.terminalTypeCode());
        assertEquals(9, dto.terminalId());
        assertEquals(1, dto.authenticationFlag());
        assertTrue(dto.abnormal());
        assertEquals(2, dto.abnormalState());
        assertEquals(10L, dto.sourceCard());
        assertEquals(20L, dto.destinationCard());
        assertEquals(30L, dto.merchantAcquirer());
        assertEquals("c", dto.op());
        assertEquals(1700000000000L, dto.ts_ms());
    }

    @Test
    void throwsRuntimeExceptionOnMalformedJson() {
        assertThrows(RuntimeException.class, () -> mapper.fromDebezium("{not valid json"));
    }

    @Test
    void throwsIllegalStateWhenPayloadIsNull() {
        String json = """
                {
                  "payload": null
                }
                """;
        assertThrows(IllegalStateException.class, () -> mapper.fromDebezium(json));
    }

    @Test
    void throwsIllegalStateWhenAfterIsAbsent() {
        // Debezium delete event — 'after' is null; should not return null silently
        String json = """
                {
                  "payload": {
                    "before": {"transaction_internal_id": 1},
                    "op": "d"
                  }
                }
                """;
        assertThrows(IllegalStateException.class, () -> mapper.fromDebezium(json));
    }
}
