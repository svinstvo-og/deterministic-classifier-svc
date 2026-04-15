package varta.deterministic_clasifying_svc.service.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import varta.deterministic_clasifying_svc.dto.ClassificationResultDto;
import varta.deterministic_clasifying_svc.dto.FlagReason;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassificationProducerServiceTest {

    @Mock
    private KafkaTemplate<String, ClassificationResultDto> kafkaTemplate;

    private static final String TOPIC = "deterministic-classification";

    private ClassificationProducerService createService() {
        return new ClassificationProducerService(kafkaTemplate, TOPIC);
    }

    @Test
    void publishesFlaggedResultToCorrectTopic() {
        ClassificationProducerService service = createService();
        ClassificationResultDto result = new ClassificationResultDto(
                42L, true, List.of(FlagReason.HIGH_VELOCITY_1H), Instant.now());

        service.publish(result);

        verify(kafkaTemplate).send(TOPIC, "42", result);
    }

    @Test
    void publishesCleanResultToCorrectTopic() {
        ClassificationProducerService service = createService();
        ClassificationResultDto result = new ClassificationResultDto(
                99L, false, List.of(), Instant.now());

        service.publish(result);

        verify(kafkaTemplate).send(TOPIC, "99", result);
    }

    @Test
    void usesTransactionIdAsKey() {
        ClassificationProducerService service = createService();
        ClassificationResultDto result = new ClassificationResultDto(
                12345L, true, List.of(FlagReason.NIGHT_TRANSACTION), Instant.now());

        service.publish(result);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(TOPIC), keyCaptor.capture(), eq(result));
        assertEquals("12345", keyCaptor.getValue());
    }
}
