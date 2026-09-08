package com.red_escuela.backend.exception;
import com.red_escuela.backend.identity.exception.DniAlreadyExistsException;
import com.red_escuela.backend.identity.exception.GoogleAccountNotLinkedException;
import com.red_escuela.backend.identity.exception.InvalidCredentialsException;
import com.red_escuela.backend.identity.exception.InvalidGoogleTokenException;
import com.red_escuela.backend.identity.exception.InvalidPasswordPolicyException;
import com.red_escuela.backend.identity.exception.InvalidPasswordResetTokenException;
import com.red_escuela.backend.identity.exception.InvalidRefreshTokenException;
import com.red_escuela.backend.identity.exception.InvalidStaffRoleException;
import com.red_escuela.backend.identity.exception.StudentNotActiveException;
import com.red_escuela.backend.identity.exception.StudentNotConfirmedException;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.exception.StudentNotRegularException;
import com.red_escuela.backend.identity.exception.TooManyRequestsException;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import com.red_escuela.backend.billing.exception.CohortAdmissionWindowClosedException;
import com.red_escuela.backend.billing.exception.CoursePaymentPlanAlreadySetException;
import com.red_escuela.backend.billing.exception.CreditNoteNotFoundException;
import com.red_escuela.backend.billing.exception.DuplicatePromotionBenefitFeeTypeException;
import com.red_escuela.backend.billing.exception.InsufficientCreditBalanceException;
import com.red_escuela.backend.billing.exception.InvalidCreditNoteReasonException;
import com.red_escuela.backend.billing.exception.InvalidAdHocFeeTypeException;
import com.red_escuela.backend.billing.exception.InvalidPaymentPlanScopeException;
import com.red_escuela.backend.billing.exception.InvalidPaymentReceiptStateException;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.exception.InvoiceNotPayableException;
import com.red_escuela.backend.billing.exception.NoActiveCollectionsPolicyException;
import com.red_escuela.backend.billing.exception.NoActivePaymentPlanException;
import com.red_escuela.backend.billing.exception.PaymentExceedsInvoiceBalanceException;
import com.red_escuela.backend.billing.exception.PaymentPlanOptionMismatchException;
import com.red_escuela.backend.billing.exception.PaymentPlanOptionNotFoundException;
import com.red_escuela.backend.billing.exception.PaymentReceiptAlreadyPendingException;
import com.red_escuela.backend.billing.exception.PaymentReceiptNotFoundException;
import com.red_escuela.backend.billing.exception.PendingDebtException;
import com.red_escuela.backend.billing.exception.PriceRevisionNotFoundException;
import com.red_escuela.backend.billing.exception.ProgramCohortNotFoundException;
import com.red_escuela.backend.billing.exception.ProgramEnrollmentMissingCohortException;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateExceedsTermSpanException;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateMismatchException;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateNotFoundException;
import com.red_escuela.backend.billing.exception.PromotionAlreadyClosedException;
import com.red_escuela.backend.billing.exception.PromotionNotFoundException;
import com.red_escuela.backend.billing.exception.ScholarshipExceedsCohortSpanException;
import com.red_escuela.backend.billing.exception.ScholarshipNotFoundException;
import com.red_escuela.backend.billing.exception.StudentInDebtException;
import com.red_escuela.backend.billing.exception.UpfrontWindowClosedException;
import com.red_escuela.backend.academicstructure.exception.InvalidPrerequisiteException;
import com.red_escuela.backend.academicstructure.exception.CircularPrerequisiteException;
import com.red_escuela.backend.academicstructure.exception.ElectiveGroupNotAssignedException;
import com.red_escuela.backend.academicstructure.exception.InvalidElectiveRangeException;
import com.red_escuela.backend.academicstructure.exception.SubjectNotFoundException;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import com.red_escuela.backend.academicstructure.exception.CourseNotFoundException;
import com.red_escuela.backend.academicstructure.exception.TermNotFoundException;
import com.red_escuela.backend.academicstructure.exception.ElectiveGroupNotFoundException;
import com.red_escuela.backend.academicstructure.exception.ProgramSubjectNotFoundException;
import com.red_escuela.backend.academicstructure.exception.CourseSubjectNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.exception.InstructorNotFoundException;
import com.red_escuela.backend.academicstructure.exception.PrerequisiteNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SectionInstructorNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SubjectAlreadyExistsException;
import com.red_escuela.backend.academicstructure.exception.ProgramSubjectAlreadyExistsException;
import com.red_escuela.backend.academicstructure.exception.ElectiveGroupAlreadyExistsException;
import com.red_escuela.backend.academicstructure.exception.PrerequisiteAlreadyExistsException;
import com.red_escuela.backend.academicstructure.exception.InstructorAlreadyAssignedException;
import com.red_escuela.backend.academicstructure.exception.ElectiveGroupInUseException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public void handleInvalidCredentials() {
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public void handleInvalidRefreshToken() {
    }

    @ExceptionHandler(InvalidPasswordResetTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public void handleInvalidPasswordResetToken() {
    }

    @ExceptionHandler(InvalidGoogleTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public void handleInvalidGoogleToken() {
    }

    @ExceptionHandler(GoogleAccountNotLinkedException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleGoogleAccountNotLinked() {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleValidation() {
    }

    // Excepciones propias de Spring MVC (no de nuestro dominio) pero perfectamente
    // alcanzables desde input de cliente real — JSON malformado o un path variable con
    // el tipo incorrecto (ej. /students/abc en vez de /students/1). Sin esto caían en
    // el manejo por default de Spring (response.sendError(400) vía
    // DefaultHandlerExceptionResolver), que dispara un redirect interno a /error — y
    // aunque /error ya es permitAll (ver SecurityConfig), Spring Boot expone ahí un
    // body JSON de error por default (timestamp/status/error/message/path), rompiendo
    // la convención del proyecto de nunca mandar body en errores. Encontrado probando
    // con qa/bruno (un JSON con `+` de concatenación JS sin evaluar, típico error de
    // escribir un test a mano, terminaba dando 403 antes del fix de /error).
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleMalformedBody() {
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handlePathVariableTypeMismatch() {
    }

    @ExceptionHandler({
            UnsupportedFileTypeException.class,
            MaxUploadSizeExceededException.class,
            DniAlreadyExistsException.class,
            InvalidStaffRoleException.class,
            InvalidPrerequisiteException.class,
            CircularPrerequisiteException.class,
            ElectiveGroupNotAssignedException.class,
            InvalidElectiveRangeException.class,
            StudentNotConfirmedException.class,
            StudentNotActiveException.class,
            AdmissionWindowClosedException.class,
            EnrollmentWindowClosedException.class,
            ProgramEnrollmentRequiredException.class,
            EnrollmentFeeNotPaidException.class,
            PrerequisiteNotMetException.class,
            AmbiguousSectionException.class,
            InvalidSectionChangeException.class,
            SubjectNotInProgramPlanException.class,
            InvalidWindowConfigurationException.class,
            StudentNotRegularException.class,
            ExamBoardRegistrationWindowClosedException.class,
            NoEvaluationSchemeException.class,
            MaxExamAttemptsExceededException.class,
            ExamBoardSubjectMismatchException.class,
            NotEligibleForGraduationException.class,
            CertificateNotEligibleException.class,
            InvalidPasswordPolicyException.class,
            InvalidSectionOverridesException.class,
            InvalidEvaluationDateException.class,
            SubmissionWindowClosedException.class,
            InvalidSectionEvaluationConfigTransitionException.class,
            InvalidPaymentPlanScopeException.class,
            ProgramYearBillingTemplateExceedsTermSpanException.class,
            ScholarshipExceedsCohortSpanException.class,
            DuplicatePromotionBenefitFeeTypeException.class,
            InvalidCreditNoteReasonException.class,
            InsufficientCreditBalanceException.class,
            InvalidAdHocFeeTypeException.class,
            // MonetaryAdjustmentValue rejects an invalid/mixed/empty fixedAmount+percentage
            // combination by throwing IllegalArgumentException from its own constructors — mapped
            // here rather than wrapped in a domain exception, since the Value Object itself is the
            // single source of truth for that validation (Fase 6.1).
            IllegalArgumentException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleBadRequest() {
    }

    @ExceptionHandler({
            StudentNotFoundException.class,
            InstitutionNotFoundException.class,
            RoleNotFoundException.class,
            PersonNotFoundException.class,
            SubjectNotFoundException.class,
            ProgramNotFoundException.class,
            CourseNotFoundException.class,
            TermNotFoundException.class,
            ElectiveGroupNotFoundException.class,
            ProgramSubjectNotFoundException.class,
            CourseSubjectNotFoundException.class,
            SectionNotFoundException.class,
            InstructorNotFoundException.class,
            EvaluationSchemeNotFoundException.class,
            PrerequisiteNotFoundException.class,
            SectionInstructorNotFoundException.class,
            ProgramEnrollmentNotFoundException.class,
            ProgramEnrollmentApplicationNotFoundException.class,
            CourseEnrollmentNotFoundException.class,
            SectionEnrollmentNotFoundException.class,
            AttendanceNotFoundException.class,
            ExamBoardNotFoundException.class,
            ExamBoardRegistrationNotFoundException.class,
            GradeItemNotFoundException.class,
            InvoiceNotFoundException.class,
            GraduationRequestNotFoundException.class,
            DirectorApprovalRequestNotFoundException.class,
            ProgramCohortNotFoundException.class,
            ProgramYearBillingTemplateNotFoundException.class,
            PaymentPlanOptionNotFoundException.class,
            PriceRevisionNotFoundException.class,
            SectionEvaluationConfigNotFoundException.class,
            NotificationNotFoundException.class,
            ClassSessionNotFoundException.class,
            AttendanceChangeRequestNotFoundException.class,
            PaymentReceiptNotFoundException.class,
            NoActiveCollectionsPolicyException.class,
            ScholarshipNotFoundException.class,
            PromotionNotFoundException.class,
            CreditNoteNotFoundException.class
    })
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleNotFound() {
    }

    @ExceptionHandler({
            ProspectAlreadyConfirmedException.class,
            SubjectAlreadyExistsException.class,
            ProgramSubjectAlreadyExistsException.class,
            ElectiveGroupAlreadyExistsException.class,
            PrerequisiteAlreadyExistsException.class,
            InstructorAlreadyAssignedException.class,
            ElectiveGroupInUseException.class,
            AlreadyEnrolledInProgramException.class,
            AlreadyEnrolledInCourseException.class,
            AlreadyEnrolledInSubjectException.class,
            CannotWithdrawException.class,
            IncompleteSectionSetupException.class,
            AttendanceAlreadyRecordedException.class,
            CannotJustifyException.class,
            ExamBoardClosedException.class,
            AlreadyRegisteredInExamBoardException.class,
            AlreadyRegisteredInAnotherExamBoardException.class,
            PendingDebtException.class,
            GraduationRequestAlreadyActiveException.class,
            DirectorApprovalRequestAlreadyActiveException.class,
            InvalidGraduationRequestStateException.class,
            InvalidDirectorApprovalRequestStateException.class,
            InvalidProgramEnrollmentApplicationStateException.class,
            AlreadyHasPendingApplicationException.class,
            UsernameAlreadyExistsException.class,
            TermEnrollmentOverrideClosedException.class,
            EvaluationSchemeInUseException.class,
            ClassSessionAlreadyCanceledException.class,
            InvalidAttendanceChangeRequestStateException.class,
            CoursePaymentPlanAlreadySetException.class,
            NoActivePaymentPlanException.class,
            UpfrontWindowClosedException.class,
            CohortAdmissionWindowClosedException.class,
            PaymentPlanOptionMismatchException.class,
            ProgramEnrollmentMissingCohortException.class,
            ProgramYearBillingTemplateMismatchException.class,
            InvoiceNotPayableException.class,
            PaymentReceiptAlreadyPendingException.class,
            InvalidPaymentReceiptStateException.class,
            PaymentExceedsInvoiceBalanceException.class,
            StudentInDebtException.class,
            PromotionAlreadyClosedException.class
    })
    @ResponseStatus(HttpStatus.CONFLICT)
    public void handleConflict() {
    }

    @ExceptionHandler({
            NotOwnSectionException.class,
            NotOwnEnrollmentException.class,
            RoleNotAssignableByActorException.class,
            NotSectionLeadInstructorException.class,
            NotOwnNotificationException.class
    })
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public void handleForbidden() {
    }

    @ExceptionHandler(TooManyRequestsException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public void handleTooManyRequests() {
    }

    // DataIntegrityViolationException sin capturar en el service (ej. un constraint
    // único violado que el código no anticipó explícitamente, como pasó en
    // RegistrationService con uk_person_email — ver su historial) mapea a 409, no a
    // 500: semánticamente es lo correcto para cualquier violación de unicidad a nivel
    // DB, y evita que la excepción escape sin manejar hasta el servlet container
    // (que redirige a /error, el cual cae en anyRequest().authenticated() de
    // SecurityConfig y devuelve un 403 vacío y engañoso — parece un problema de
    // permisos cuando es un error de datos). Encontrado probando con qa/bruno.
    //
    // A propósito NO se agrega acá un catch-all de Exception.class: un
    // @RestControllerAdvice corre dentro del dispatch de Spring MVC, así que
    // capturaría también AccessDeniedException (lanzada por @PreAuthorize) y
    // convertiría 403 reales en 500 — probado y confirmado con
    // unAlumnoNoPuedeAprobarSuPropiaSolicitud, que empezó a fallar apenas se agregó
    // un catch-all así. Cualquier excepción realmente inesperada (ni acá ni en la
    // lista de arriba) ahora sí llega a /error con su status real (permitAll en
    // SecurityConfig) — ya no queda enmascarada como 403, mismo criterio que el resto
    // de esta clase de todas formas: no manejar lo que no se conoce todavía, dejar
    // que Spring Boot muestre su error default en vez de inventar un mapeo a ciegas.
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void handleDataIntegrityViolation() {
    }

    // @Version (optimistic locking) sin capturar en el service: dos transacciones que leyeron la
    // misma fila y la mutan de forma mutuamente excluyente (ej. aprobar/rechazar el mismo
    // PaymentReceipt en paralelo) — la que commitea segunda pierde acá, 409 semánticamente
    // correcto para "alguien más ya cambió esto debajo tuyo", mismo criterio que el resto de esta
    // clase para conflictos de concurrencia.
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void handleOptimisticLockingFailure() {
    }
}
