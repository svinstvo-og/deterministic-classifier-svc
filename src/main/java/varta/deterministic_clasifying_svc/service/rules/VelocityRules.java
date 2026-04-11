package varta.deterministic_clasifying_svc.service.rules;

import org.springframework.stereotype.Component;
import varta.deterministic_clasifying_svc.config.RuleConfig;
import varta.deterministic_clasifying_svc.dto.FlagReason;
import varta.deterministic_clasifying_svc.model.Transaction;
import varta.deterministic_clasifying_svc.repository.VelocityRepository;

import java.util.ArrayList;
import java.util.List;

@Component
public class VelocityRules implements Rule {

    private final RuleConfig ruleConfig;
    private final VelocityRepository velocityRepository;

    public VelocityRules(RuleConfig ruleConfig, VelocityRepository velocityRepository) {
        this.ruleConfig = ruleConfig;
        this.velocityRepository = velocityRepository;
    }

    @Override
    public List<FlagReason> evaluate(Transaction transaction) {
        List<FlagReason> reasons = new ArrayList<>();

        // Record the transaction in Redis before querying so the current tx is included
        long epochNow = System.currentTimeMillis() / 1000;
        if (transaction.getSourceCard() != null && transaction.getTransactionInternalId() != null) {
            velocityRepository.recordTransaction(
                    transaction.getSourceCard(),
                    transaction.getMerchantAcquirer(),
                    transaction.getTransactionInternalId(),
                    epochNow
            );
        }

        checkHighVelocity1H(transaction, reasons);
        checkHighVelocity24H(transaction, reasons);
        checkHighMerchantSpread1H(transaction, reasons);
        checkRapidSequential(transaction, reasons);
        checkVelocitySpike(transaction, reasons);

        return reasons;
    }

    // RULE-V01: flag when >= 5 transactions in the last 1 hour
    private void checkHighVelocity1H(Transaction tx, List<FlagReason> reasons) {
        if (tx.getVelocity1H() != null && tx.getVelocity1H() >= ruleConfig.getHighVelocity1HThreshold()) {
            reasons.add(FlagReason.HIGH_VELOCITY_1H);
        }
    }

    // RULE-V02: flag when >= 15 transactions in the last 24 hours
    private void checkHighVelocity24H(Transaction tx, List<FlagReason> reasons) {
        if (tx.getVelocity24H() != null && tx.getVelocity24H() >= ruleConfig.getHighVelocity24HThreshold()) {
            reasons.add(FlagReason.HIGH_VELOCITY_24H);
        }
    }

    // RULE-V03: flag when >= 4 distinct merchants used in the last 1 hour (Redis-tracked)
    private void checkHighMerchantSpread1H(Transaction tx, List<FlagReason> reasons) {
        if (tx.getSourceCard() == null) {
            return;
        }
        long distinctMerchants = velocityRepository.getDistinctMerchantCount1H(tx.getSourceCard());
        if (distinctMerchants >= ruleConfig.getHighMerchantSpread1HThreshold()) {
            reasons.add(FlagReason.HIGH_MERCHANT_SPREAD_1H);
        }
    }

    // RULE-V04: flag when < 60 seconds since last transaction AND velocity1H >= 2
    private void checkRapidSequential(Transaction tx, List<FlagReason> reasons) {
        if (tx.getSecondsSinceLastTransaction() != null && tx.getVelocity1H() != null
                && tx.getSecondsSinceLastTransaction() < ruleConfig.getRapidSequentialSeconds()
                && tx.getVelocity1H() >= ruleConfig.getRapidSequentialMinVelocity()) {
            reasons.add(FlagReason.RAPID_SEQUENTIAL_TRANSACTIONS);
        }
    }

    // RULE-V05: flag when velocity1H > (30D avg per hour) * 10, requires >= 10 recorded transactions
    private void checkVelocitySpike(Transaction tx, List<FlagReason> reasons) {
        if (tx.getSourceCard() == null || tx.getVelocity1H() == null) {
            return;
        }
        long count30D = velocityRepository.getTransactionCount30D(tx.getSourceCard());
        if (count30D < ruleConfig.getVelocitySpikeMin30DTransactions()) {
            // Not enough history to establish a reliable baseline
            return;
        }
        double historicalAvgPerHour = count30D / (double) (30 * 24);
        if (tx.getVelocity1H() > historicalAvgPerHour * ruleConfig.getVelocitySpikeMultiplier()) {
            reasons.add(FlagReason.VELOCITY_SPIKE);
        }
    }
}
