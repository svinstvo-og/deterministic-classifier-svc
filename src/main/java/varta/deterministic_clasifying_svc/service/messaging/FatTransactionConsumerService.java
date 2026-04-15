package varta.deterministic_clasifying_svc.service.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.stereotype.Service;
import org.springframework.retry.annotation.Backoff;
import varta.deterministic_clasifying_svc.dto.ClassificationResultDto;
import varta.deterministic_clasifying_svc.dto.CreditTransactionDto;
import varta.deterministic_clasifying_svc.dto.FlagReason;
import varta.deterministic_clasifying_svc.model.Transaction;
import varta.deterministic_clasifying_svc.service.ClassifyingService;
import varta.deterministic_clasifying_svc.util.CreditTransactionMapper;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class FatTransactionConsumerService {

    private final CreditTransactionMapper creditTransactionMapper;
    private final ClassifyingService classifyingService;
    private final ClassificationProducerService classificationProducerService;

    public FatTransactionConsumerService(
            CreditTransactionMapper creditTransactionMapper,
            ClassifyingService classifyingService,
            ClassificationProducerService classificationProducerService) {
        this.creditTransactionMapper = creditTransactionMapper;
        this.classifyingService = classifyingService;
        this.classificationProducerService = classificationProducerService;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(topics = "dbserver1.public.credit_trans", groupId = "deterministic-classifying-svc")
    public void listen(String message) {
        if (message.isBlank()) {
            throw new IllegalArgumentException("Empty message body ");
        }
        log.info("Accepted new credit transaction message, length: {}", message.length());

        CreditTransactionDto transactionDto = creditTransactionMapper.fromDebezium(message);
        Transaction transaction = Transaction.fromDto(transactionDto);

        List<FlagReason> reasons = classifyingService.classify(transaction);

        ClassificationResultDto result = new ClassificationResultDto(
                transaction.getTransactionInternalId(),
                !reasons.isEmpty(),
                reasons,
                Instant.now()
        );

        classificationProducerService.publish(result);
        log.info("Classified transaction {}: flagged={}, reasons={}",
                result.transactionInternalId(), result.flaggedAbnormal(), result.flagReasons());
    }
}
