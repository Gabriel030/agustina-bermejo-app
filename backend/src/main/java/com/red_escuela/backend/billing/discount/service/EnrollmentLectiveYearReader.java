package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.configuration.service.YearCycleFactory;
import com.red_escuela.backend.billing.discount.domain.EnrollmentKind;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.LectiveYearSpan;
import com.red_escuela.backend.billing.planning.domain.YearCycles;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFacts;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.Optional;

@Component
public class EnrollmentLectiveYearReader {
    private final ProgramYearBillingTemplateRepository templateRepository;
    private final YearCycleFactory cycleFactory;

    public EnrollmentLectiveYearReader(ProgramYearBillingTemplateRepository templateRepository, YearCycleFactory cycleFactory) {
        this.templateRepository = templateRepository;
        this.cycleFactory = cycleFactory;
    }

    public Optional<LectiveYearSpan> findLectiveYearCovering(EnrollmentReference enrollment, BillingEnrollmentFacts facts, YearMonth period) {
        if (period == null) {
            return Optional.empty();
        }
        if (enrollment.kind() == EnrollmentKind.COURSE) {
            return facts.courseTermStart() == null || facts.courseTermEnd() == null
                    ? Optional.empty()
                    : Optional.of(new LectiveYearSpan(facts.courseTermStart(), facts.courseTermEnd())).filter(span -> span.contains(period));
        }
        if (facts.programId() == null) {
            return Optional.empty();
        }
        YearCycles cycles = new YearCycles(cycleFactory.cyclesOf(templateRepository.findAllByProgramId(facts.programId()), period.getYear()), facts.billingTemplateId());
        return cycles.covering(period).map(cycle -> new LectiveYearSpan(cycle.start(), cycle.end()));
    }
}
