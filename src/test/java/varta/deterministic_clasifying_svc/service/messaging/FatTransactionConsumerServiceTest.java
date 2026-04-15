package varta.deterministic_clasifying_svc.service.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import varta.deterministic_clasifying_svc.dto.ClassificationResultDto;
import varta.deterministic_clasifying_svc.dto.CreditTransactionDto;
import varta.deterministic_clasifying_svc.dto.FlagReason;
import varta.deterministic_clasifying_svc.service.ClassifyingService;
import varta.deterministic_clasifying_svc.util.CreditTransactionMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FatTransactionConsumerServiceTest {

    @Mock
    private CreditTransactionMapper creditTransactionMapper;

    @Mock
    private ClassifyingService classifyingService;

    @Mock
    private ClassificationProducerService classificationProducerService;

    @InjectMocks
    private FatTransactionConsumerService service;

    private static final String VALID_MESSAGE =
            "{\"payload\":{\"after\":{\"transaction_internal_id\":1},\"source\":{}}}";

    private CreditTransactionDto sampleDto() {
        return new CreditTransactionDto(
                1L, "PAN123", false, 100L, 999,
                BigDecimal.valueOf(250.00), "COMP_KEY",
                LocalDateTime.of(2024, 1, 15, 14, 30),
                0, 5, "PURCHASE", 1, 100,
                1, false, 0,
                1000L, 2000L, 3000L,
                "c", System.currentTimeMillis(),
                2, 5, false, 600L
        );
    }

    @Test
    void throwsIllegalArgumentOnBlankMessage() {
        assertThrows(IllegalArgumentException.class, () -> service.listen("   "));
        verifyNoInteractions(creditTransactionMapper);
        verifyNoInteractions(classifyingService);
        verifyNoInteractions(classificationProducerService);
    }

    @Test
    void fullPipelineForCleanTransaction() {
        CreditTransactionDto dto = sampleDto();
        when(creditTransactionMapper.fromDebezium(VALID_MESSAGE)).thenReturn(dto);
        when(classifyingService.classify(any())).thenReturn(List.of());

        service.listen(VALID_MESSAGE);

        verify(creditTransactionMapper).fromDebezium(VALID_MESSAGE);
        verify(classifyingService).classify(any());

        ArgumentCaptor<ClassificationResultDto> captor =
                ArgumentCaptor.forClass(ClassificationResultDto.class);
        verify(classificationProducerService).publish(captor.capture());

        ClassificationResultDto result = captor.getValue();
        assertEquals(1L, result.transactionInternalId());
        assertFalse(result.flaggedAbnormal());
        assertTrue(result.flagReasons().isEmpty());
        assertNotNull(result.classifiedAt());
    }

    @Test
    void fullPipelineForFlaggedTransaction() {
        CreditTransactionDto dto = sampleDto();
        List<FlagReason> flags = List.of(FlagReason.HIGH_VELOCITY_1H, FlagReason.NIGHT_TRANSACTION);
        when(creditTransactionMapper.fromDebezium(VALID_MESSAGE)).thenReturn(dto);
        when(classifyingService.classify(any())).thenReturn(flags);

        service.listen(VALID_MESSAGE);

        ArgumentCaptor<ClassificationResultDto> captor =
                ArgumentCaptor.forClass(ClassificationResultDto.class);
        verify(classificationProducerService).publish(captor.capture());

        ClassificationResultDto result = captor.getValue();
        assertEquals(1L, result.transactionInternalId());
        assertTrue(result.flaggedAbnormal());
        assertEquals(2, result.flagReasons().size());
        assertTrue(result.flagReasons().contains(FlagReason.HIGH_VELOCITY_1H));
        assertTrue(result.flagReasons().contains(FlagReason.NIGHT_TRANSACTION));
    }

    @Test
    void propagatesMapperExceptions() {
        when(creditTransactionMapper.fromDebezium(VALID_MESSAGE))
                .thenThrow(new RuntimeException("parse error"));

        assertThrows(RuntimeException.class, () -> service.listen(VALID_MESSAGE));
        verifyNoInteractions(classifyingService);
        verifyNoInteractions(classificationProducerService);
    }

    @Test
    void propagatesClassifyingExceptions() {
        when(creditTransactionMapper.fromDebezium(VALID_MESSAGE)).thenReturn(sampleDto());
        when(classifyingService.classify(any())).thenThrow(new RuntimeException("classify error"));

        assertThrows(RuntimeException.class, () -> service.listen(VALID_MESSAGE));
        verifyNoInteractions(classificationProducerService);
    }

    @Test
    void propagatesProducerExceptions() {
        when(creditTransactionMapper.fromDebezium(VALID_MESSAGE)).thenReturn(sampleDto());
        when(classifyingService.classify(any())).thenReturn(List.of());
        doThrow(new RuntimeException("publish error"))
                .when(classificationProducerService).publish(any());

        assertThrows(RuntimeException.class, () -> service.listen(VALID_MESSAGE));
    }
}
