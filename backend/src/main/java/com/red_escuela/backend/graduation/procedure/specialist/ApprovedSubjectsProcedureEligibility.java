package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.policy.ApprovedSubjectsBlocker;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.policy.ApprovedSubjectsEligibilityPolicy;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsEligibilityFacts;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.OwnedEnrollment;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureEligibilityResponse;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureIneligibilityCode;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureIneligibilityReason;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureNextStep;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureNextStepKind;
import com.red_escuela.backend.graduation.procedure.dto.StudentProcedureType;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class ApprovedSubjectsProcedureEligibility implements ProcedureEligibilityStrategy {

    private final OwnProgramEnrollmentResolver enrollmentResolver;
    private final ApprovedSubjectsEligibilityFacts eligibilityFacts;
    private final ApprovedSubjectsEligibilityPolicy eligibilityPolicy = new ApprovedSubjectsEligibilityPolicy();
    private final Clock clock;

    public ApprovedSubjectsProcedureEligibility(OwnProgramEnrollmentResolver enrollmentResolver,
                                                ApprovedSubjectsEligibilityFacts eligibilityFacts, Clock clock) {
        this.enrollmentResolver = enrollmentResolver;
        this.eligibilityFacts = eligibilityFacts;
        this.clock = clock;
    }

    @Override
    public StudentProcedureType type() {
        return StudentProcedureType.APPROVED_SUBJECTS_CERTIFICATE;
    }

    @Override
    public ProcedureEligibilityResponse eligibilityOf(Long studentId, Long enrollmentId, Long registrationId) {
        OwnProgramEnrollmentResolver.Resolution resolution = enrollmentResolver.resolve(studentId, enrollmentId);
        if (resolution.enrollmentId().isEmpty()) {
            return response(null, List.of(noEnrollmentReason(resolution.ambiguous())));
        }
        Long resolved = resolution.enrollmentId().get();
        List<ApprovedSubjectsBlocker> blockers = eligibilityPolicy.blockersOf(
                eligibilityFacts.inputFor(studentId, new OwnedEnrollment(resolved, true), LocalDateTime.now(clock)));
        return response(resolved, blockers.stream().map(ApprovedSubjectsProcedureEligibility::reasonOf).toList());
    }

    private ProcedureIneligibilityReason noEnrollmentReason(boolean ambiguous) {
        String message = ambiguous
                ? "Tenés más de una carrera activa: elegí para cuál querés la constancia"
                : "No encontramos una carrera activa para generar la constancia";
        return reason(ProcedureIneligibilityCode.NO_ACTIVE_ENROLLMENT, message,
                ProcedureNextStepKind.CONTACT_ADMINISTRATION, "Contactar a administración");
    }

    private static ProcedureIneligibilityReason reasonOf(ApprovedSubjectsBlocker blocker) {
        return switch (blocker) {
            case NO_ACTIVE_ENROLLMENT -> reason(ProcedureIneligibilityCode.NO_ACTIVE_ENROLLMENT,
                    "No encontramos una carrera activa para generar la constancia",
                    ProcedureNextStepKind.CONTACT_ADMINISTRATION, "Contactar a administración");
            case NOT_REGULAR_STUDENT -> reason(ProcedureIneligibilityCode.NOT_REGULAR_STUDENT, "Todavía no sos alumno regular",
                    ProcedureNextStepKind.CONTACT_ADMINISTRATION, "Contactar a administración");
            case PENDING_PAYMENTS -> reason(ProcedureIneligibilityCode.PENDING_PAYMENTS, "Tenés pagos pendientes",
                    ProcedureNextStepKind.GO_TO_PAYMENTS, "Ir a Pagos");
            case ISSUANCE_LIMIT_REACHED -> reason(ProcedureIneligibilityCode.ISSUANCE_LIMIT_REACHED,
                    "Ya generaste hoy esta constancia. Si sigue vigente, podés volver a descargarla desde Trámites",
                    ProcedureNextStepKind.TRY_AGAIN_LATER, "Volver a intentar mañana");
            case NO_APPROVED_SUBJECTS -> reason(ProcedureIneligibilityCode.NO_SUBJECTS_IN_PROGRESS,
                    "Todavía no tenés materias aprobadas",
                    ProcedureNextStepKind.CONTACT_ADMINISTRATION, "Contactar a administración");
        };
    }

    private static ProcedureEligibilityResponse response(Long enrollmentId, List<ProcedureIneligibilityReason> reasons) {
        return new ProcedureEligibilityResponse(StudentProcedureType.APPROVED_SUBJECTS_CERTIFICATE, reasons.isEmpty(), enrollmentId,
                reasons.isEmpty() ? "Podés generar tu constancia de materias aprobadas ahora" : null, reasons);
    }

    private static ProcedureIneligibilityReason reason(ProcedureIneligibilityCode code, String message, ProcedureNextStepKind kind,
                                                       String label) {
        return new ProcedureIneligibilityReason(code, message, new ProcedureNextStep(kind, label, null));
    }
}
