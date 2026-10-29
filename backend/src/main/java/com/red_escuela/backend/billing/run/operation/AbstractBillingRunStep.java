package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.run.domain.BillingRunItemRecord;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import com.red_escuela.backend.billing.run.domain.BillingRunSubjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractBillingRunStep {
    public static final String SUBJECT_PROCESSING_FAILED = "SUBJECT_PROCESSING_FAILED";
    private static final String DECISION_CODE_KEY = "decisionCode";
    private static final String MESSAGE_KEY = "message";

    private final Logger log = LoggerFactory.getLogger(getClass());

    public abstract BillingRunStep step();

    protected abstract BillingRunStepOutcome executeStepForBusinessDate(LocalDate businessDate);

    public Optional<String> findNotApplicableCode() {
        return Optional.empty();
    }

    public final BillingRunStepOutcome runStepForBusinessDate(LocalDate businessDate) {
        long start = System.currentTimeMillis();
        BillingRunStepOutcome outcome = executeStepForBusinessDate(businessDate);
        log.info("Paso nocturno {}: fecha={}, candidatos={}, procesados={}, omitidos={}, fallidos={}, duracion={}ms", step(), businessDate, outcome.candidates(),
                outcome.processed(), outcome.skipped(), outcome.failed(), System.currentTimeMillis() - start);
        return outcome;
    }

    protected static List<BillingRunItemRecord> translateReportedErrors(List<Map<String, Object>> errors, String subjectKey, BillingRunSubjectType subjectType) {
        List<BillingRunItemRecord> items = new ArrayList<>();
        errors.forEach(error -> items.add(translateReportedError(error, subjectKey, subjectType)));
        return items;
    }

    private static BillingRunItemRecord translateReportedError(Map<String, Object> error, String subjectKey, BillingRunSubjectType subjectType) {
        Long subjectId = ((Number) error.get(subjectKey)).longValue();
        String message = (String) error.get(MESSAGE_KEY);
        return Optional.ofNullable((String) error.get(DECISION_CODE_KEY))
                .map(decisionCode -> BillingRunItemRecord.skippedSubject(subjectType, subjectId, decisionCode, message))
                .orElseGet(() -> BillingRunItemRecord.failedSubject(subjectType, subjectId, SUBJECT_PROCESSING_FAILED, message));
    }
}
