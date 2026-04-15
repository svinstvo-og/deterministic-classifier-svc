package varta.deterministic_clasifying_svc.service.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import varta.deterministic_clasifying_svc.dto.ClassificationResultDto;

@Service
@Slf4j
public class ClassificationProducerService {

    private final KafkaTemplate<String, ClassificationResultDto> kafkaTemplate;
    private final String outputTopic;

    public ClassificationProducerService(
            KafkaTemplate<String, ClassificationResultDto> kafkaTemplate,
            @Value("${classification.output-topic}") String outputTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.outputTopic = outputTopic;
    }

    public void publish(ClassificationResultDto result) {
        String key = String.valueOf(result.transactionInternalId());
        log.info("Publishing classification result for transaction {} to topic {}",
                key, outputTopic);
        kafkaTemplate.send(outputTopic, key, result);
    }
}
