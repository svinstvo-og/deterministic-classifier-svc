package varta.deterministic_clasifying_svc.dto;

import java.time.Instant;
import java.util.List;

public record ClassificationResultDto(
        Long transactionInternalId,
        Boolean flaggedAbnormal,
        List<FlagReason> flagReasons,
        Instant classifiedAt
) { }
