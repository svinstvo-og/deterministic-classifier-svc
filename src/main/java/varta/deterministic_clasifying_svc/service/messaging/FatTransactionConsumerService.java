package varta.deterministic_clasifying_svc.service.messaging;
import com.fasterxml.jackson.annotation.JsonGetter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.stereotype.Service;
import org.springframework.retry.annotation.Backoff;
import varta.deterministic_clasifying_svc.dto.CreditTransactionDto;
import varta.deterministic_clasifying_svc.model.Transaction;
import varta.deterministic_clasifying_svc.util.CreditTransactionMapper;

@Service
@Slf4j
public class FatTransactionConsumerService {

    private final CreditTransactionMapper creditTransactionMapper;

    public FatTransactionConsumerService(CreditTransactionMapper creditTransactionMapper) {
        this.creditTransactionMapper = creditTransactionMapper;
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
        log.info("Accepted new credit transaction message, length: {}, payload: {}",
                message.length(),
                message.split("payload")[1].split("source")[0]);
        CreditTransactionDto transactionDto = creditTransactionMapper.fromDebezium(message);
        log.info("Processed transaction: {}", transactionDto.toString());
    }
}
