package com.red_escuela.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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
            PaymentExceedsInvoiceBalanceException.class,
            NotEligibleForGraduationException.class,
            CertificateNotEligibleException.class,
            InvalidPasswordPolicyException.class,
            InvalidPricingTargetException.class,
            InvalidSectionOverridesException.class,
            InvalidProgramCohortException.class
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
            FeeScheduleNotFoundException.class,
            PaymentReceiptNotFoundException.class,
            ProgramCohortNotFoundException.class
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
            InvoiceAlreadyCancelledException.class,
            PendingDebtException.class,
            GraduationRequestAlreadyActiveException.class,
            InvalidGraduationRequestStateException.class,
            InvalidDirectorApprovalRequestStateException.class,
            InvalidProgramEnrollmentApplicationStateException.class,
            AlreadyHasPendingApplicationException.class,
            UsernameAlreadyExistsException.class,
            TermEnrollmentOverrideClosedException.class,
            InvalidPaymentReceiptStateException.class,
            PaymentReceiptAlreadyPendingException.class
    })
    @ResponseStatus(HttpStatus.CONFLICT)
    public void handleConflict() {
    }

    @ExceptionHandler({
            NotOwnSectionException.class,
            RoleNotAssignableByActorException.class
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
}
