package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.service.LateFeeCorrectionOutcome;
import com.red_escuela.backend.billing.collections.service.LateFeeWithdrawalCorrectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LateFeeWithdrawalCorrectionStep {
    private static final Logger log = LoggerFactory.getLogger(LateFeeWithdrawalCorrectionStep.class);
    private static final int PAGE_SIZE = 200;

    private final LateFeeWithdrawalCorrectionService correctionService;

    public LateFeeWithdrawalCorrectionStep(LateFeeWithdrawalCorrectionService correctionService) {
        this.correctionService = correctionService;
    }

    public LateFeeCorrectionSweepResult correctLateFeesChargedAfterTheEnrollmentEnd(LocalDate today) {
        return correctLateFeesChargedAfterTheEnrollmentEnd(today, HeldSubjectFinder.NONE);
    }

    public LateFeeCorrectionSweepResult correctLateFeesChargedAfterTheEnrollmentEnd(LocalDate today, HeldSubjectFinder heldCharges) {
        int candidates = 0;
        int corrected = 0;
        int deferred = 0;
        int failed = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        Long lastId = 0L;
        while (true) {
            List<Long> batch = correctionService.findChargeIdsWithUncorrectedDaysAfter(lastId, PAGE_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            candidates += batch.size();
            Map<Long, String> held = heldCharges.findHoldCodesAmong(batch);
            for (Long chargeId : batch) {
                if (held.containsKey(chargeId)) {
                    Map<String, Object> heldCharge = new LinkedHashMap<>();
                    heldCharge.put("lateFeeChargeId", chargeId);
                    heldCharge.put("decisionCode", held.get(chargeId));
                    heldCharge.put("message", HeldSubjectFinder.messageOf(held.get(chargeId)));
                    errors.add(heldCharge);
                    continue;
                }
                try {
                    LateFeeCorrectionOutcome outcome = correctionService.correctLateFeeCharge(chargeId, today);
                    if (outcome == LateFeeCorrectionOutcome.CORRECTED) {
                        corrected++;
                    } else if (outcome == LateFeeCorrectionOutcome.DEFERRED) {
                        deferred++;
                    }
                } catch (Exception e) {
                    failed++;
                    log.error("Falló la corrección de respaldo del recargo del cargo id={}", chargeId, e);
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("lateFeeChargeId", chargeId);
                    error.put("message", e.getMessage());
                    errors.add(error);
                }
            }
            lastId = batch.get(batch.size() - 1);
        }
        return new LateFeeCorrectionSweepResult(candidates, corrected, deferred, failed, errors);
    }
}
