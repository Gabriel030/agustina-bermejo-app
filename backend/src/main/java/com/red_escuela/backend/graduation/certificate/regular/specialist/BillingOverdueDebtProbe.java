package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.billing.api.CertificateEnrollmentFeeCheck;
import com.red_escuela.backend.billing.api.StudentOverdueDebtQuery;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import org.springframework.stereotype.Component;

@Component
public class BillingOverdueDebtProbe implements OverdueDebtProbe {

    private final StudentOverdueDebtQuery studentOverdueDebtQuery;
    private final CertificateEnrollmentFeeCheck certificateEnrollmentFeeCheck;

    public BillingOverdueDebtProbe(StudentOverdueDebtQuery studentOverdueDebtQuery,
                                   CertificateEnrollmentFeeCheck certificateEnrollmentFeeCheck) {
        this.studentOverdueDebtQuery = studentOverdueDebtQuery;
        this.certificateEnrollmentFeeCheck = certificateEnrollmentFeeCheck;
    }

    @Override
    public boolean hasOverdueDebt(Long studentId) {
        return studentOverdueDebtQuery.hasOverdueDebt(studentId);
    }

    @Override
    public boolean isEnrollmentFeePaid(RegularityKind kind, Long enrollmentId) {
        return kind == RegularityKind.COURSE || certificateEnrollmentFeeCheck.isProgramEnrollmentFeeSettled(enrollmentId);
    }
}
