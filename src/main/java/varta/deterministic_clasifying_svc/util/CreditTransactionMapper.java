package varta.deterministic_clasifying_svc.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import varta.deterministic_clasifying_svc.dto.DebeziumEnvelope;
import varta.deterministic_clasifying_svc.dto.DebeziumPayload;
import varta.deterministic_clasifying_svc.model.Transaction;

@Component
public class CreditTransactionMapper {

    final ObjectMapper objectMapper;

    public CreditTransactionMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Transaction fromDebezium(String json) {
        try {
            DebeziumEnvelope<Transaction> envelope =
                    objectMapper.readValue(json, new TypeReference<DebeziumEnvelope<Transaction>> () {});

            DebeziumPayload<Transaction> payload = envelope.payload();

            if (payload == null) {
                throw new IllegalStateException("No 'after' payload");
            }

            return envelope.payload().after();

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize Debezium message: " + e);
        }
    }
}
