package varta.deterministic_clasifying_svc.service.rules;

import varta.deterministic_clasifying_svc.dto.FlagReason;
import varta.deterministic_clasifying_svc.model.Transaction;

import java.util.List;

public interface Rule {
    List<FlagReason> evaluate(Transaction transaction);
}
