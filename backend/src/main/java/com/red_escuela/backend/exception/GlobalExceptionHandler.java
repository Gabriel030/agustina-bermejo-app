package com.red_escuela.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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
            InvalidPasswordPolicyException.class
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
            CourseEnrollmentNotFoundException.class,
            SectionEnrollmentNotFoundException.class,
            AttendanceNotFoundException.class,
            ExamBoardNotFoundException.class,
            ExamBoardRegistrationNotFoundException.class,
            GradeItemNotFoundException.class,
            InvoiceNotFoundException.class,
            GraduationRequestNotFoundException.class,
            DirectorApprovalRequestNotFoundException.class
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
            InvoiceAlreadyCancelledException.class,
            PendingDebtException.class,
            GraduationRequestAlreadyActiveException.class,
            InvalidGraduationRequestStateException.class,
            InvalidDirectorApprovalRequestStateException.class,
            TermEnrollmentOverrideClosedException.class
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
}
