package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RegularityLossRevoker {

    public static final String REGULARITY_LOST_REASON =
            "El alumno dejó de cumplir las condiciones de regularidad después de emitida la constancia";

    private final RegularityEvaluator regularityEvaluator;
    private final RegularEnrollmentCertificateRevoker revoker;

    public RegularityLossRevoker(RegularityEvaluator regularityEvaluator, RegularEnrollmentCertificateRevoker revoker) {
        this.regularityEvaluator = regularityEvaluator;
        this.revoker = revoker;
    }

    @Transactional
    public void revokeIfNoLongerRegular(RegularityKind kind, Long enrollmentId) {
        if (revoker.hasValidCertificate(kind, enrollmentId)
                && !regularityEvaluator.evaluate(kind, enrollmentId).institutionallyRegular()) {
            revoker.revokeValidCertificate(kind, enrollmentId, REGULARITY_LOST_REASON);
        }
    }
}
