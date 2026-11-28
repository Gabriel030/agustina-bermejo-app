package com.red_escuela.backend.platform.specialist;

import com.red_escuela.backend.platform.exception.BillingActivationBlockedByCohortsWithoutTemplateException;
import com.red_escuela.backend.shared.domain.TemplateLessCohortPort;
import com.red_escuela.backend.shared.module.BillingMode;
import org.springframework.stereotype.Service;

@Service
public class BillingActivationCohortGuard {

    private final TemplateLessCohortPort templateLessCohortPort;

    public BillingActivationCohortGuard(TemplateLessCohortPort templateLessCohortPort) {
        this.templateLessCohortPort = templateLessCohortPort;
    }

    public void requireNoCohortsWithoutTemplate(BillingMode from, BillingMode to) {
        if (from != BillingMode.OFF || to == BillingMode.OFF) {
            return;
        }
        long cohorts = templateLessCohortPort.countCohortsWithoutBillingTemplate();
        if (cohorts > 0) {
            throw new BillingActivationBlockedByCohortsWithoutTemplateException(cohorts);
        }
    }
}
