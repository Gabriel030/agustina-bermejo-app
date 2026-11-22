package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.PaymentStanding;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.specialist.PaymentStandingResolver;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularityEvaluator;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureEligibilityResponse;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureIneligibilityCode;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureIneligibilityReason;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureNextStep;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureNextStepKind;
import com.red_escuela.backend.graduation.procedure.dto.StudentProcedureType;
import com.red_escuela.backend.platform.api.BillingModeGate;
import com.red_escuela.backend.shared.module.BillingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RegularProcedureEligibility {

    private final OwnProgramEnrollmentResolver enrollmentResolver;
    private final RegularityEvaluator regularityEvaluator;
    private final PaymentStandingResolver paymentStandingResolver;
    private final BillingModeGate billingModeGate;
    private final DailyIssuanceLimit dailyIssuanceLimit;
    private final ValidRegularCertificateProbe validCertificateProbe;

    public RegularProcedureEligibility(OwnProgramEnrollmentResolver enrollmentResolver, RegularityEvaluator regularityEvaluator,
                                       PaymentStandingResolver paymentStandingResolver, BillingModeGate billingModeGate,
                                       DailyIssuanceLimit dailyIssuanceLimit, ValidRegularCertificateProbe validCertificateProbe) {
        this.enrollmentResolver = enrollmentResolver;
        this.regularityEvaluator = regularityEvaluator;
        this.paymentStandingResolver = paymentStandingResolver;
        this.billingModeGate = billingModeGate;
        this.dailyIssuanceLimit = dailyIssuanceLimit;
        this.validCertificateProbe = validCertificateProbe;
    }

    public ProcedureEligibilityResponse evaluate(Long studentId, Long enrollmentId) {
        OwnProgramEnrollmentResolver.Resolution resolution = enrollmentResolver.resolve(studentId, enrollmentId);
        if (resolution.enrollmentId().isEmpty()) {
            return response(null, List.of(noEnrollmentReason(resolution.ambiguous())));
        }
        Long resolved = resolution.enrollmentId().get();
        List<ProcedureIneligibilityReason> reasons = new ArrayList<>();
        if (!regularityEvaluator.evaluate(RegularityKind.PROGRAM, resolved).institutionallyRegular()) {
            reasons.add(reason(ProcedureIneligibilityCode.NOT_REGULAR_STUDENT, "Todavía no sos alumno regular",
                    ProcedureNextStepKind.CONTACT_ADMINISTRATION, "Contactar a administración"));
        }
        addPaymentReason(reasons, resolved);
        addIssuanceReason(reasons, studentId, resolved);
        return response(resolved, reasons);
    }

    private ProcedureIneligibilityReason noEnrollmentReason(boolean ambiguous) {
        String message = ambiguous
                ? "Tenés más de una carrera activa: elegí para cuál querés la constancia"
                : "No encontramos una carrera activa para generar la constancia";
        return reason(ProcedureIneligibilityCode.NO_ACTIVE_ENROLLMENT, message,
                ProcedureNextStepKind.CONTACT_ADMINISTRATION, "Contactar a administración");
    }

    private void addPaymentReason(List<ProcedureIneligibilityReason> reasons, Long enrollmentId) {
        BillingMode mode = billingModeGate.currentMode();
        if (mode == BillingMode.MIRROR) {
            reasons.add(reason(ProcedureIneligibilityCode.PENDING_PAYMENTS, "Comunicate con Administración",
                    ProcedureNextStepKind.CONTACT_ADMINISTRATION, "Contactar a administración"));
        } else if (mode == BillingMode.FULL
                && paymentStandingResolver.resolve(RegularityKind.PROGRAM, enrollmentId) == PaymentStanding.OVERDUE) {
            reasons.add(reason(ProcedureIneligibilityCode.PENDING_PAYMENTS, "Tenés pagos pendientes",
                    ProcedureNextStepKind.GO_TO_PAYMENTS, "Ir a Pagos"));
        }
    }

    private void addIssuanceReason(List<ProcedureIneligibilityReason> reasons, Long studentId, Long enrollmentId) {
        if (validCertificateProbe.existsForProgramEnrollment(enrollmentId)) {
            reasons.add(reason(ProcedureIneligibilityCode.ISSUANCE_LIMIT_REACHED,
                    "Ya generaste esta constancia. Si sigue vigente, podés volver a descargarla desde Trámites",
                    ProcedureNextStepKind.TRY_AGAIN_LATER, "Descargarla desde Trámites"));
        } else if (dailyIssuanceLimit.isReachedForRegular(studentId)) {
            reasons.add(reason(ProcedureIneligibilityCode.ISSUANCE_LIMIT_REACHED,
                    "Ya generaste hoy esta constancia. Si sigue vigente, podés volver a descargarla desde Trámites",
                    ProcedureNextStepKind.TRY_AGAIN_LATER, "Volver a intentar mañana"));
        }
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
