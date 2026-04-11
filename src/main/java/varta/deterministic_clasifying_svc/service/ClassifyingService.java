package varta.deterministic_clasifying_svc.service;

import org.springframework.stereotype.Service;
import varta.deterministic_clasifying_svc.dto.FlagReason;
import varta.deterministic_clasifying_svc.model.Transaction;
import varta.deterministic_clasifying_svc.service.rules.Rule;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClassifyingService {

    private final List<Rule> rules;

    public ClassifyingService(List<Rule> rules) {
        this.rules = rules;
    }

    public List<FlagReason> classify(Transaction transaction) {
        List<FlagReason> reasons = rules.stream()
                .flatMap(rule -> rule.evaluate(transaction).stream())
                .collect(Collectors.toList());

        if (!reasons.isEmpty()) {
            transaction.setFlaggedAbnormal(true);
            transaction.setFlagReasons(reasons);
        }
        return reasons;
    }
}
