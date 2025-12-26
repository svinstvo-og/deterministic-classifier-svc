package varta.deterministic_clasifying_svc.service.messaging;
import org.springframework.kafka.annotation.KafkaListener;

import org.springframework.stereotype.Service;

@Service
public class FatTransactionConsumerService {

    @KafkaListener(topics = "fat-transactions", groupId = "deterministic-classifying-svc")
    public void listen(String message) {
        // TODO logic
        System.out.println("Received Message: " + message);
    }
}
