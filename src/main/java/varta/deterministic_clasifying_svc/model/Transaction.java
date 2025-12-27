package varta.deterministic_clasifying_svc.model;

import varta.deterministic_clasifying_svc.dto.FlagReason;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Transaction {
    private Long transactionInternalId;
    private LocalDateTime processedAt;

    //For statistical purposes only, real classifier wouldnt have such field obviously
    private boolean abnormal;

    private boolean flaggedAbnormal;
    private FlagReason flagReason;

    private Long destinationCard;
    private Long merchantAcquirer;
}
