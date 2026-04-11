package varta.deterministic_clasifying_svc.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import varta.deterministic_clasifying_svc.dto.CreditTransactionDto;
import varta.deterministic_clasifying_svc.dto.FlagReason;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@ToString
@Builder
public class Transaction {
    private Long transactionInternalId;
    private LocalDateTime processedAt;
    private BigDecimal amount;

    //For statistical purposes only, real classifier wouldnt have such field obviously
    private Boolean abnormal;

    private Boolean flaggedAbnormal;
    private List<FlagReason> flagReasons;

    private Long sourceCard;
    private Long destinationCard;
    private Long merchantAcquirer;

    // Pre-computed enrichment fields from ingestion service
    private Integer velocity1H;
    private Integer velocity24H;
    private Boolean isNight;
    private Long secondsSinceLastTransaction;
    private Integer authenticationFlag;

    public static Transaction fromDto(CreditTransactionDto dto) {
        return new TransactionBuilder()
                .transactionInternalId(dto.transactionInternalId())
                .processedAt(dto.processedAt())
                .amount(dto.transactionAmount())
                .abnormal(dto.abnormal())
                .sourceCard(dto.sourceCard())
                .destinationCard(dto.destinationCard())
                .merchantAcquirer(dto.merchantAcquirer())
                .velocity1H(dto.velocity1H())
                .velocity24H(dto.velocity24H())
                .isNight(dto.isNight())
                .secondsSinceLastTransaction(dto.secondsSinceLastTransaction())
                .authenticationFlag(dto.authenticationFlag())
                .build();
    }
}
