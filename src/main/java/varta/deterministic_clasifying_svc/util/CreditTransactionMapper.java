package varta.deterministic_clasifying_svc.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import varta.deterministic_clasifying_svc.dto.CreditTransactionDto;
import varta.deterministic_clasifying_svc.dto.DebeziumEnvelope;
import varta.deterministic_clasifying_svc.dto.DebeziumPayload;

@Component
@Slf4j
public class CreditTransactionMapper {

    final ObjectMapper objectMapper;

    public CreditTransactionMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CreditTransactionDto fromDebezium(String json) {
        try {
            DebeziumEnvelope<CreditTransactionDto> envelope =
                    objectMapper.readValue(json, new TypeReference<DebeziumEnvelope<CreditTransactionDto>> () {});
            log.info(envelope.toString());

            DebeziumPayload<CreditTransactionDto> payload = envelope.payload();
            log.info(payload.toString());

            if (payload == null) {
                throw new IllegalStateException("No 'after' payload");
            }

            return envelope.payload().after();

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize Debezium message: " + e);
        }
    }
}
