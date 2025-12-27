package varta.deterministic_clasifying_svc.service.messaging;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.KafkaListener;

import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.stereotype.Service;
import org.springframework.retry.annotation.Backoff;
import varta.deterministic_clasifying_svc.dto.CreditTransactionDto;

@Service
public class FatTransactionConsumerService {

    @RetryableTopic(
            attempts = "3",
            backOff = @BackOff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(topics = "dbserver1.public.credit_trans", groupId = "deterministic-classifying-svc")
    public void listen(String message) {
        System.out.println("Received Message: " + message);

//        CreditTransactionDto creditTransactionDto =

    }
}
