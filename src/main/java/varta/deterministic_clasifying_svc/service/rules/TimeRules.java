package varta.deterministic_clasifying_svc.service.rules;

import org.springframework.stereotype.Component;
import varta.deterministic_clasifying_svc.config.RuleConfig;
import varta.deterministic_clasifying_svc.dto.FlagReason;
import varta.deterministic_clasifying_svc.model.Transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class TimeRules implements Rule {

    private final RuleConfig ruleConfig;

    public TimeRules(RuleConfig ruleConfig) {
        this.ruleConfig = ruleConfig;
    }

    @Override
    public List<FlagReason> evaluate(Transaction transaction) {
        List<FlagReason> reasons = new ArrayList<>();

        checkNightTransaction(transaction, reasons);
        checkRapidSequential(transaction, reasons);
        checkDormantCardReactivation(transaction, reasons);
        checkNewCardHighValue(transaction, reasons);

        return reasons;
    }

    // RULE-T01: flag transactions occurring during night hours
    private void checkNightTransaction(Transaction tx, List<FlagReason> reasons) {
        if (tx.getIsNight() != null) {
            if (tx.getIsNight()) {
                reasons.add(FlagReason.NIGHT_TRANSACTION);
            }
            return;
        }
        // Fallback: derive from processedAt hour
        if (tx.getProcessedAt() == null) return;
        int hour = tx.getProcessedAt().getHour();
        if (hour >= ruleConfig.getNightStartHour() && hour <= ruleConfig.getNightEndHour()) {
            reasons.add(FlagReason.NIGHT_TRANSACTION);
        }
    }

    // RULE-T02: flag transactions that follow a prior transaction very rapidly
    private void checkRapidSequential(Transaction tx, List<FlagReason> reasons) {
        if (tx.getSecondsSinceLastTransaction() == null) return;
        long seconds = tx.getSecondsSinceLastTransaction();
        if (seconds < ruleConfig.getRapidSequentialHighSeconds()
                || seconds < ruleConfig.getRapidSequentialModerateSeconds()) {
            reasons.add(FlagReason.RAPID_SEQUENTIAL_TRANSACTIONS);
        }
    }

    // RULE-T03: flag when card was dormant for a long period and is now used for a significant amount
    private void checkDormantCardReactivation(Transaction tx, List<FlagReason> reasons) {
        if (tx.getSecondsSinceLastTransaction() == null) return;
        long daysSinceLastTx = tx.getSecondsSinceLastTransaction() / 86400;

        if (daysSinceLastTx > ruleConfig.getDormantDaysHigh()) {
            if (tx.getAmount() != null
                    && tx.getAmount().compareTo(BigDecimal.valueOf(ruleConfig.getDormantAmountThreshold())) > 0) {
                reasons.add(FlagReason.DORMANT_CARD_REACTIVATION);
                return;
            }
        }
        if (daysSinceLastTx > ruleConfig.getDormantDaysModerate()) {
            reasons.add(FlagReason.DORMANT_CARD_REACTIVATION);
        }
    }

    // RULE-T04: flag when a new card's first transaction is high-value
    private void checkNewCardHighValue(Transaction tx, List<FlagReason> reasons) {
        // TODO: implement when per-card historical transaction count is available.
        // Logic: if historicalTransactionCount <= 1 && amount > 500 → flag
        //        if historicalTransactionCount == 0 && amount > 200 && isTransfer → flag
        // FlagReason: NEW_CARD_HIGH_VALUE_FIRST_TX
    }
}
