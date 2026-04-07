package varta.deterministic_clasifying_svc.service.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import varta.deterministic_clasifying_svc.dto.CreditTransactionDto;
import varta.deterministic_clasifying_svc.util.CreditTransactionMapper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FatTransactionConsumerServiceTest {

    @Mock
    private CreditTransactionMapper creditTransactionMapper;

    @InjectMocks
    private FatTransactionConsumerService service;

    // Message containing both "payload" and "source" substrings, which the log line requires
    private static final String VALID_MESSAGE =
            "{\"payload\":{\"after\":{\"transaction_internal_id\":1},\"source\":{}}}";

    @Test
    void throwsIllegalArgumentOnBlankMessage() {
        assertThrows(IllegalArgumentException.class, () -> service.listen("   "));
        verifyNoInteractions(creditTransactionMapper);
    }

    @Test
    void delegatesToMapperOnValidMessage() {
        when(creditTransactionMapper.fromDebezium(VALID_MESSAGE))
                .thenReturn(mock(CreditTransactionDto.class));

        service.listen(VALID_MESSAGE);

        verify(creditTransactionMapper).fromDebezium(VALID_MESSAGE);
    }

    @Test
    void propagatesMapperExceptions() {
        when(creditTransactionMapper.fromDebezium(VALID_MESSAGE))
                .thenThrow(new RuntimeException("parse error"));

        assertThrows(RuntimeException.class, () -> service.listen(VALID_MESSAGE));
    }
}
