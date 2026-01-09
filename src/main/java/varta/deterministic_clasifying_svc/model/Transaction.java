package varta.deterministic_clasifying_svc.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import varta.deterministic_clasifying_svc.dto.CreditTransactionDto;
import varta.deterministic_clasifying_svc.dto.FlagReason;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    private FlagReason flagReason;

    private Long destinationCard;
    private Long merchantAcquirer;

    public static Transaction fromDto(CreditTransactionDto dto) {
        return new TransactionBuilder()
                .transactionInternalId(dto.transactionInternalId())
                .processedAt(dto.processedAt())
                .amount(dto.transactionAmount())
                .abnormal(dto.abnormal())
                .destinationCard(dto.destinationCard())
                .merchantAcquirer(dto.merchantAcquirer())
                .build();
    }
}
