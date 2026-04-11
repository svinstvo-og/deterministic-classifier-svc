package varta.deterministic_clasifying_svc.service.rules;

import org.springframework.stereotype.Component;
import varta.deterministic_clasifying_svc.config.RuleConfig;
import varta.deterministic_clasifying_svc.dto.FlagReason;
import varta.deterministic_clasifying_svc.model.Transaction;
import varta.deterministic_clasifying_svc.repository.VelocityRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class StructuringRules implements Rule {

    private final RuleConfig ruleConfig;
    private final VelocityRepository velocityRepository;

    public StructuringRules(RuleConfig ruleConfig, VelocityRepository velocityRepository) {
        this.ruleConfig = ruleConfig;
        this.velocityRepository = velocityRepository;
    }

    @Override
    public List<FlagReason> evaluate(Transaction transaction) {
        List<FlagReason> reasons = new ArrayList<>();
        recordForStatefulRules(transaction);
        checkSubThresholdStructuring(transaction, reasons);
        checkFanOutTransfer(transaction, reasons);
        checkPassThroughAccount(transaction, reasons);
        return reasons;
    }

    private void recordForStatefulRules(Transaction tx) {
        if (!Boolean.TRUE.equals(tx.getIsTransfer())) return;
        if (tx.getSourceCard() == null || tx.getDestinationCard() == null
                || tx.getTransactionInternalId() == null || tx.getAmount() == null) return;
        velocityRepository.recordDestination(
                tx.getSourceCard(), tx.getDestinationCard(),
                tx.getAmount(), tx.getTransactionInternalId(),
                System.currentTimeMillis() / 1000
        );
    }

    // RULE-S01: Sub-Threshold Structuring — STUB
    // TODO: implement when per-card daily total aggregation is available.
    // Logic: amount >= 9000 && < 10000 → suspicious; multiple in 24H → flag
    //        every tx in 24H < 999.99 but total > 3000 → flag
    // FlagReason: STRUCTURING_SUB_THRESHOLD
    private void checkSubThresholdStructuring(Transaction tx, List<FlagReason> reasons) {
        // stub — no-op
    }

    // RULE-S02: Fan-Out Transfer Pattern
    private void checkFanOutTransfer(Transaction tx, List<FlagReason> reasons) {
        if (!Boolean.TRUE.equals(tx.getIsTransfer())) return;
        if (tx.getSourceCard() == null) return;

        long distinctDest = velocityRepository.getDistinctDestinationCount1H(tx.getSourceCard());
        BigDecimal totalTransferred = velocityRepository.getTotalTransferred1H(tx.getSourceCard());

        if (distinctDest >= ruleConfig.getFanOutDistinctDestinations()) {
            reasons.add(FlagReason.FAN_OUT_TRANSFER);
            return;
        }
        if (totalTransferred.compareTo(BigDecimal.valueOf(ruleConfig.getFanOutTotalThreshold())) > 0
                && distinctDest >= 2) {
            reasons.add(FlagReason.FAN_OUT_TRANSFER);
        }
    }

    // RULE-S03: Pass-Through Account — STUB
    // TODO: implement when inbound transfer tracking is available per-card.
    // Logic: outbound24H >= inbound24H * 0.8 && inbound24H > 500 → flag
    // FlagReason: PASS_THROUGH_ACCOUNT
    private void checkPassThroughAccount(Transaction tx, List<FlagReason> reasons) {
        // stub — no-op
    }
}
