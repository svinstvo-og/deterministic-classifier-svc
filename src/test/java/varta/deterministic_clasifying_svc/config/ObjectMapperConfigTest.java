package varta.deterministic_clasifying_svc.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import varta.deterministic_clasifying_svc.dto.CreditTransactionDto;

import static org.junit.jupiter.api.Assertions.*;

class ObjectMapperConfigTest {

    private final ObjectMapper objectMapper = new ObjectMapperConfig().objectMapper();

    @Test
    void createsNonNullObjectMapper() {
        assertNotNull(objectMapper);
    }

    @Test
    void failOnUnknownPropertiesIsDisabled() {
        assertFalse(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    @Test
    void unknownFieldsDoNotThrowOnDeserialization() {
        // CreditTransactionDto has a fixed set of known fields; extra fields must be ignored
        String json = """
                {
                  "transaction_internal_id": 1,
                  "completely_unknown_field": "should_be_ignored"
                }
                """;
        assertDoesNotThrow(() -> objectMapper.readValue(json, CreditTransactionDto.class));
    }
}
