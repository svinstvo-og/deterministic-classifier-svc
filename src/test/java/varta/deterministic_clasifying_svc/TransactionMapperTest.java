package varta.deterministic_clasifying_svc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import varta.deterministic_clasifying_svc.dto.CreditTransactionDto;
import varta.deterministic_clasifying_svc.util.CreditTransactionMapper;

@SpringBootTest
public class TransactionMapperTest {

    private final CreditTransactionMapper creditTransactionMapper = new CreditTransactionMapper(new ObjectMapper());

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
        CreditTransactionDto dto = creditTransactionMapper.fromDebezium(json);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(123L, dto.transactionInternalId());
    }

}
