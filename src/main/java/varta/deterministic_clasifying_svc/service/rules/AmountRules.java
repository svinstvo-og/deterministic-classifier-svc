package varta.deterministic_clasifying_svc.service.rules;

import org.springframework.stereotype.Component;
import varta.deterministic_clasifying_svc.config.RuleConfig;
import varta.deterministic_clasifying_svc.dto.FlagReason;
import varta.deterministic_clasifying_svc.model.Transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class AmountRules implements Rule {

    private final RuleConfig ruleConfig;

    public AmountRules(RuleConfig ruleConfig) {
        this.ruleConfig = ruleConfig;
    }

    @Override
    public List<FlagReason> evaluate(Transaction transaction) {
        List<FlagReason> reasons = new ArrayList<>();

        checkZScoreOutlier(transaction, reasons);
        checkRatioToMedian(transaction, reasons);
        checkHistoricalMax(transaction, reasons);
        checkRoundNumberAmount(transaction, reasons);
        checkMicroCharge(transaction, reasons);
        checkAuthThreshold(transaction, reasons);

        return reasons;
    }

    // RULE-A01: flag when transaction amount is a statistical outlier (high z-score)
    private void checkZScoreOutlier(Transaction tx, List<FlagReason> reasons) {
        // TODO: implement when zScore is added to FatTransactionDto and mapped to Transaction
    }

    // RULE-A02: flag when transaction amount is a large multiple of the card's median spend
    private void checkRatioToMedian(Transaction tx, List<FlagReason> reasons) {
        // TODO: implement when ratioToMedian is added to FatTransactionDto and mapped to Transaction
    }

    // RULE-A03: flag when transaction amount exceeds the card's all-time maximum
    private void checkHistoricalMax(Transaction tx, List<FlagReason> reasons) {
        // TODO: implement when per-card historical max is tracked (requires new Redis key or external store)
    }

    // RULE-A04: flag suspiciously round amounts (divisible by 500 >= 500, or divisible by 100 >= 200)
    private void checkRoundNumberAmount(Transaction tx, List<FlagReason> reasons) {
        if (tx.getAmount() == null) return;
        BigDecimal amount = tx.getAmount();
        BigDecimal fiveHundred = BigDecimal.valueOf(500);
        BigDecimal hundred = BigDecimal.valueOf(100);
        if (amount.remainder(fiveHundred).compareTo(BigDecimal.ZERO) == 0
                && amount.compareTo(BigDecimal.valueOf(ruleConfig.getRoundNumberHighMin())) >= 0) {
            reasons.add(FlagReason.ROUND_NUMBER_AMOUNT);
        } else if (amount.remainder(hundred).compareTo(BigDecimal.ZERO) == 0
                && amount.compareTo(BigDecimal.valueOf(ruleConfig.getRoundNumberMin())) >= 0) {
            reasons.add(FlagReason.ROUND_NUMBER_AMOUNT);
        }
    }

    // RULE-A05: flag micro-charges (< 1.00) paired with high velocity or rapid repeat
    private void checkMicroCharge(Transaction tx, List<FlagReason> reasons) {
        if (tx.getAmount() == null) return;
        if (tx.getAmount().compareTo(BigDecimal.valueOf(ruleConfig.getMicroChargeMax())) >= 0) return;
        boolean highVelocity = tx.getVelocity1H() != null && tx.getVelocity1H() >= 2;
        boolean recentTx = tx.getSecondsSinceLastTransaction() != null
                && tx.getSecondsSinceLastTransaction() < 300;
        if (highVelocity || recentTx) {
            reasons.add(FlagReason.MICRO_CHARGE_CARD_TESTING);
        }
    }

    // RULE-A06: flag amounts in common "just below auth threshold" bands without authentication
    private void checkAuthThreshold(Transaction tx, List<FlagReason> reasons) {
        if (tx.getAmount() == null) return;
        if (tx.getAuthenticationFlag() != null && tx.getAuthenticationFlag() != 0) return;
        BigDecimal amount = tx.getAmount();
        boolean lowBand = amount.compareTo(BigDecimal.valueOf(ruleConfig.getAuthThresholdLowMin())) >= 0
                && amount.compareTo(BigDecimal.valueOf(ruleConfig.getAuthThresholdLowMax())) <= 0;
        boolean highBand = amount.compareTo(BigDecimal.valueOf(ruleConfig.getAuthThresholdHighMin())) >= 0
                && amount.compareTo(BigDecimal.valueOf(ruleConfig.getAuthThresholdHighMax())) <= 0;
        if (lowBand || highBand) {
            reasons.add(FlagReason.AMOUNT_BELOW_AUTH_THRESHOLD);
        }
    }
}
