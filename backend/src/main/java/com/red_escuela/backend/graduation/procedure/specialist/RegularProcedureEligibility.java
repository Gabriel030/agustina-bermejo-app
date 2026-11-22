package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.PaymentStanding;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularityChecker;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureEligibilityResponse;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureIneligibilityCode;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureIneligibilityReason;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureNextStep;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureNextStepKind;
import com.red_escuela.backend.graduation.procedure.dto.StudentProcedureType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RegularProcedureEligibility {

    private final OwnProgramEnrollmentResolver enrollmentResolver;
    private final RegularityChecker regularityChecker;
    private final DailyIssuanceLimit dailyIssuanceLimit;

    public RegularProcedureEligibility(OwnProgramEnrollmentResolver enrollmentResolver, RegularityChecker regularityChecker,
                                       DailyIssuanceLimit dailyIssuanceLimit) {
        this.enrollmentResolver = enrollmentResolver;
        this.regularityChecker = regularityChecker;
        this.dailyIssuanceLimit = dailyIssuanceLimit;
    }

    public ProcedureEligibilityResponse evaluate(Long studentId, Long enrollmentId) {
        Optional<Long> resolved = enrollmentResolver.resolve(studentId, enrollmentId);
        if (resolved.isEmpty()) {
            return response(null, List.of(reason(ProcedureIneligibilityCode.NO_ACTIVE_ENROLLMENT,
                    "No encontramos una carrera activa para generar la constancia",
                    ProcedureNextStepKind.CONTACT_ADMINISTRATION, "Contactar a administración")));
        }
        RegularityResponse regularity = regularityChecker.check(RegularityKind.PROGRAM, resolved.get());
        List<ProcedureIneligibilityReason> reasons = new ArrayList<>();
        if (!regularity.regular()) {
            reasons.add(reason(ProcedureIneligibilityCode.NOT_REGULAR_STUDENT, "Todavía no sos alumno regular",
                    ProcedureNextStepKind.CONTACT_ADMINISTRATION, "Contactar a administración"));
        }
        if (regularity.paymentStanding() == PaymentStanding.OVERDUE) {
            reasons.add(reason(ProcedureIneligibilityCode.PENDING_PAYMENTS, "Tenés pagos pendientes",
                    ProcedureNextStepKind.GO_TO_PAYMENTS, "Ir a Pagos"));
        }
        if (dailyIssuanceLimit.isReachedForRegular(studentId)) {
            reasons.add(reason(ProcedureIneligibilityCode.ISSUANCE_LIMIT_REACHED,
                    "Ya generaste hoy esta constancia. Si sigue vigente, podés volver a descargarla desde Trámites",
                    ProcedureNextStepKind.TRY_AGAIN_LATER, "Volver a intentar mañana"));
        }
        return response(resolved.get(), reasons);
    }

    private ProcedureEligibilityResponse response(Long enrollmentId, List<ProcedureIneligibilityReason> reasons) {
        return new ProcedureEligibilityResponse(StudentProcedureType.REGULAR_STUDENT_CERTIFICATE, reasons.isEmpty(), enrollmentId,
                reasons.isEmpty() ? "Podés generar tu constancia de alumno regular ahora" : null, reasons);
    }

    private ProcedureIneligibilityReason reason(ProcedureIneligibilityCode code, String message, ProcedureNextStepKind kind,
                                                String label) {
        return new ProcedureIneligibilityReason(code, message, new ProcedureNextStep(kind, label, null));
    }
}
