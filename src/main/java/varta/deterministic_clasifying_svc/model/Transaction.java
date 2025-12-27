package varta.deterministic_clasifying_svc.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Transaction {
    private Long transactionInternalId;
    private LocalDateTime processedAt;

    //For statistical purposes only, real classifier wouldnt have such field obviously
    private boolean abnormal;

    private boolean flaggedAbnormal;

    private Long destinationCard;
    private Long merchantAcquirer;
}
