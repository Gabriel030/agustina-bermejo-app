package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.exception.IncompleteSectionEvaluationConfigException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class SectionEvaluationConfigCompletenessValidator {

    public void requireComplete(SectionEvaluationConfig config) {
        EvaluationScheme scheme = config.sectionEvaluationScheme();
        List<String> missing = new ArrayList<>();
        if (config.getTpCount() == null) {
            missing.add("tpCount");
        }
        int examCount = scheme == null || scheme.getExamCountExpected() == null ? 0 : scheme.getExamCountExpected();
        List<LocalDate> examDates = Arrays.asList(
                config.getExam1Date(), config.getExam2Date(), config.getExam3Date(), config.getExam4Date());
        for (int index = 0; index < Math.min(examCount, examDates.size()); index++) {
            if (examDates.get(index) == null) {
                missing.add("exam" + (index + 1) + "Date");
            }
        }
        boolean requiresFinal = scheme != null && Boolean.TRUE.equals(scheme.getHasFinal());
        if (requiresFinal && config.getFinalCall1Date() == null) {
            missing.add("finalCall1Date");
        }
        if (!missing.isEmpty()) {
            throw new IncompleteSectionEvaluationConfigException(missing);
        }
    }
}
