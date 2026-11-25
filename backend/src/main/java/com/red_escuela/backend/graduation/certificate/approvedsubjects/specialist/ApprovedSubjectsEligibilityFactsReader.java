package com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist;

import com.red_escuela.backend.enrollment.api.ApprovedSubjectGradesReader;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.specialist.OverdueDebtProbe;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularityEvaluator;
import com.red_escuela.backend.graduation.documentsettings.specialist.DocumentIssuanceSettingsFinder;
import com.red_escuela.backend.platform.api.BillingModeGate;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.policy.ApprovedSubjectsEligibilityInput;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsEligibilityFacts;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.OwnedEnrollment;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.repository.ApprovedSubjectsCertificateRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class ApprovedSubjectsEligibilityFactsReader implements ApprovedSubjectsEligibilityFacts {

    private final BillingModeGate billingModeGate;
    private final OverdueDebtProbe overdueDebtProbe;
    private final RegularityEvaluator regularityEvaluator;
    private final ApprovedSubjectsCertificateRepository repository;
    private final DocumentIssuanceSettingsFinder settingsFinder;
    private final ApprovedSubjectGradesReader gradesReader;

    public ApprovedSubjectsEligibilityFactsReader(BillingModeGate billingModeGate, OverdueDebtProbe overdueDebtProbe,
                                                  RegularityEvaluator regularityEvaluator,
                                                  ApprovedSubjectsCertificateRepository repository,
                                                  DocumentIssuanceSettingsFinder settingsFinder,
                                                  ApprovedSubjectGradesReader gradesReader) {
        this.billingModeGate = billingModeGate;
        this.overdueDebtProbe = overdueDebtProbe;
        this.regularityEvaluator = regularityEvaluator;
        this.repository = repository;
        this.settingsFinder = settingsFinder;
        this.gradesReader = gradesReader;
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovedSubjectsEligibilityInput inputFor(Long studentId, OwnedEnrollment enrollment, LocalDateTime now) {
        BillingMode mode = billingModeGate.currentMode();
        Long enrollmentId = enrollment.enrollmentId();
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        return new ApprovedSubjectsEligibilityInput(mode,
                mode == BillingMode.FULL && overdueDebtProbe.hasOverdueDebt(studentId),
                regularityEvaluator.evaluate(RegularityKind.PROGRAM, enrollmentId).institutionallyRegular(),
                repository.countByStudentIdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(studentId, dayStart, dayStart.plusDays(1)),
                settingsFinder.current().dailyIssuanceLimitPerType(),
                gradesReader.listApprovedSubjectGrades(studentId, enrollmentId).size(),
                enrollment.active());
    }
}
