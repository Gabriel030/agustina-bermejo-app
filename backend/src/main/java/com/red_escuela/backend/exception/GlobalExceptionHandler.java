package com.red_escuela.backend.exception;
import com.red_escuela.backend.announcement.exception.AnnouncementAlreadySentException;
import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.exception.CannotChangeAudienceOfSentAnnouncementException;
import com.red_escuela.backend.announcement.exception.ScheduledDateInThePastException;
import com.red_escuela.backend.announcement.exception.EmptyAudienceException;
import com.red_escuela.backend.announcement.exception.SegmentNotAvailableForSectionException;
import com.red_escuela.backend.announcement.exception.UnknownAudienceSegmentException;
import com.red_escuela.backend.announcement.exception.NotSectionInstructorException;
import com.red_escuela.backend.identity.exception.DniAlreadyExistsException;
import com.red_escuela.backend.identity.exception.GoogleAccountNotLinkedException;
import com.red_escuela.backend.identity.exception.InvalidCredentialsException;
import com.red_escuela.backend.identity.exception.InvalidGoogleTokenException;
import com.red_escuela.backend.identity.exception.InvalidCurrentPasswordException;
import com.red_escuela.backend.identity.exception.InvalidPasswordPolicyException;
import com.red_escuela.backend.identity.exception.InvalidEmailVerificationTokenException;
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
import com.red_escuela.backend.academicstructure.exception.ClassroomNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SectionScheduleConflictException;
import com.red_escuela.backend.academicstructure.exception.CycleNotFoundException;
import com.red_escuela.backend.academicstructure.exception.CycleAlreadyInProgressException;
import com.red_escuela.backend.academicstructure.exception.CycleIncompleteException;
import com.red_escuela.backend.academicstructure.exception.IncompleteSubjectException;
import com.red_escuela.backend.academicstructure.exception.CurricularYearOutOfRangeException;
import com.red_escuela.backend.academicstructure.exception.InvalidRegistrationWindowException;
import com.red_escuela.backend.academicstructure.exception.InvalidTermDatesException;
import com.red_escuela.backend.academicstructure.exception.InvalidProgramSubjectCategoryException;
import com.red_escuela.backend.academicstructure.exception.SubjectNotAssignableToPlanException;
import com.red_escuela.backend.academicstructure.exception.CycleNotPendingApprovalException;
import com.red_escuela.backend.academicstructure.exception.CycleNotEditableException;
import com.red_escuela.backend.academicstructure.exception.SectionCapacityExceedsClassroomException;
import com.red_escuela.backend.academicstructure.exception.InvalidPrerequisiteException;
import com.red_escuela.backend.academicstructure.exception.InvalidCorequisiteException;
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
import com.red_escuela.backend.academicstructure.exception.CorequisiteNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SectionInstructorNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SubjectAlreadyExistsException;
import com.red_escuela.backend.academicstructure.exception.SubjectInvalidTransitionException;
import com.red_escuela.backend.academicstructure.exception.ProgramSubjectAlreadyExistsException;
import com.red_escuela.backend.academicstructure.exception.ElectiveGroupAlreadyExistsException;
import com.red_escuela.backend.academicstructure.exception.PrerequisiteAlreadyExistsException;
import com.red_escuela.backend.academicstructure.exception.CorequisiteAlreadyExistsException;
import com.red_escuela.backend.academicstructure.exception.InstructorAlreadyAssignedException;
import com.red_escuela.backend.academicstructure.exception.ElectiveGroupInUseException;
import com.red_escuela.backend.enrollment.exception.AdmissionWindowClosedException;
import com.red_escuela.backend.attendance.exception.ClassOnHolidayException;
import com.red_escuela.backend.enrollment.exception.EnrollmentWindowClosedException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentRequiredException;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeNotPaidException;
import com.red_escuela.backend.enrollment.exception.PrerequisiteNotMetException;
import com.red_escuela.backend.enrollment.exception.AmbiguousSectionException;
import com.red_escuela.backend.enrollment.exception.InvalidSectionChangeException;
import com.red_escuela.backend.enrollment.exception.SubjectNotInProgramPlanException;
import com.red_escuela.backend.enrollment.exception.InvalidWindowConfigurationException;
import com.red_escuela.backend.enrollment.exception.InvalidSectionOverridesException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentApplicationNotFoundException;
import com.red_escuela.backend.enrollment.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.DirectorApprovalRequestNotFoundException;
import com.red_escuela.backend.enrollment.exception.ProspectAlreadyConfirmedException;
import com.red_escuela.backend.enrollment.exception.AlreadyEnrolledInProgramException;
import com.red_escuela.backend.enrollment.exception.AlreadyEnrolledInCourseException;
import com.red_escuela.backend.enrollment.exception.PendingCourseEnrollmentApplicationException;
import com.red_escuela.backend.enrollment.exception.AlreadyEnrolledInSubjectException;
import com.red_escuela.backend.enrollment.exception.CannotWithdrawException;
import com.red_escuela.backend.enrollment.exception.IncompleteSectionSetupException;
import com.red_escuela.backend.enrollment.exception.DirectorApprovalRequestAlreadyActiveException;
import com.red_escuela.backend.enrollment.exception.InvalidDirectorApprovalRequestStateException;
import com.red_escuela.backend.enrollment.exception.AlreadyHasPendingApplicationException;
import com.red_escuela.backend.billing.exception.MercadoPagoGatewayException;
import com.red_escuela.backend.enrollment.exception.TermEnrollmentOverrideClosedException;
import com.red_escuela.backend.evaluation.exception.ExamBoardRegistrationWindowClosedException;
import com.red_escuela.backend.evaluation.exception.NoEvaluationSchemeException;
import com.red_escuela.backend.evaluation.exception.MaxExamAttemptsExceededException;
import com.red_escuela.backend.evaluation.exception.ExamBoardSubjectMismatchException;
import com.red_escuela.backend.evaluation.exception.InvalidEvaluationDateException;
import com.red_escuela.backend.evaluation.exception.SubmissionWindowClosedException;
import com.red_escuela.backend.evaluation.exception.InvalidSectionEvaluationConfigTransitionException;
import com.red_escuela.backend.evaluation.exception.EvaluationSchemeNotFoundException;
import com.red_escuela.backend.evaluation.exception.ExamBoardNotFoundException;
import com.red_escuela.backend.evaluation.exception.ExamBoardRegistrationNotFoundException;
import com.red_escuela.backend.evaluation.exception.GradeItemNotFoundException;
import com.red_escuela.backend.evaluation.exception.SectionEvaluationConfigNotFoundException;
import com.red_escuela.backend.evaluation.exception.ExamBoardClosedException;
import com.red_escuela.backend.evaluation.exception.AlreadyRegisteredInExamBoardException;
import com.red_escuela.backend.evaluation.exception.AlreadyRegisteredInAnotherExamBoardException;
import com.red_escuela.backend.evaluation.exception.EvaluationSchemeInUseException;
import com.red_escuela.backend.evaluation.exception.NotOwnSectionException;
import com.red_escuela.backend.evaluation.exception.NotSectionLeadInstructorException;
import com.red_escuela.backend.attendance.exception.AttendanceNotFoundException;
import com.red_escuela.backend.attendance.exception.ClassSessionNotFoundException;
import com.red_escuela.backend.attendance.exception.AttendanceChangeRequestNotFoundException;
import com.red_escuela.backend.attendance.exception.CannotJustifyException;
import com.red_escuela.backend.attendance.exception.ClassSessionAlreadyCanceledException;
import com.red_escuela.backend.attendance.exception.InvalidAttendanceChangeRequestStateException;
import com.red_escuela.backend.graduation.exception.CertificateNotEligibleException;
import com.red_escuela.backend.graduation.exception.GraduationRequestNotFoundException;
import com.red_escuela.backend.graduation.exception.GraduationRequestAlreadyActiveException;
import com.red_escuela.backend.graduation.exception.InvalidGraduationRequestStateException;
import com.red_escuela.backend.graduation.exception.NotEligibleForGraduationException;
import com.red_escuela.backend.notification.exception.NotOwnNotificationException;
import com.red_escuela.backend.notification.exception.NotificationNotFoundException;

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

    @ExceptionHandler(InvalidEmailVerificationTokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleInvalidEmailVerificationToken() {
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
            InvalidCorequisiteException.class,
            CircularPrerequisiteException.class,
            ElectiveGroupNotAssignedException.class,
            InvalidElectiveRangeException.class,
            StudentNotConfirmedException.class,
            StudentNotActiveException.class,
            AdmissionWindowClosedException.class,
            ClassOnHolidayException.class,
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
            InvalidCurrentPasswordException.class,
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
            IllegalArgumentException.class,
            CycleIncompleteException.class,
            SectionCapacityExceedsClassroomException.class,
            IncompleteSubjectException.class,
            CurricularYearOutOfRangeException.class,
            InvalidRegistrationWindowException.class,
            InvalidProgramSubjectCategoryException.class,
            InvalidTermDatesException.class,
            EmptyAudienceException.class,
            ScheduledDateInThePastException.class,
            SegmentNotAvailableForSectionException.class,
            UnknownAudienceSegmentException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorCodeResponse handleBadRequest(Exception exception) {
        return ErrorCodeResponse.of(exception);
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
            CorequisiteNotFoundException.class,
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
            CreditNoteNotFoundException.class,
            ClassroomNotFoundException.class,
            CycleNotFoundException.class,
            AnnouncementNotFoundException.class
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
            CorequisiteAlreadyExistsException.class,
            InstructorAlreadyAssignedException.class,
            ElectiveGroupInUseException.class,
            AlreadyEnrolledInProgramException.class,
            AlreadyEnrolledInCourseException.class,
            PendingCourseEnrollmentApplicationException.class,
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
            PromotionAlreadyClosedException.class,
            SectionScheduleConflictException.class,
            CycleAlreadyInProgressException.class,
            CycleNotPendingApprovalException.class,
            CycleNotEditableException.class,
            SubjectInvalidTransitionException.class,
            SubjectNotAssignableToPlanException.class,
            AnnouncementAlreadySentException.class,
            CannotChangeAudienceOfSentAnnouncementException.class
    })
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorCodeResponse handleConflict(Exception exception) {
        return ErrorCodeResponse.of(exception);
    }

    @ExceptionHandler({
            NotOwnSectionException.class,
            NotOwnEnrollmentException.class,
            RoleNotAssignableByActorException.class,
            NotSectionLeadInstructorException.class,
            NotOwnNotificationException.class,
            NotSectionInstructorException.class
    })
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public void handleForbidden() {
    }

    @ExceptionHandler(TooManyRequestsException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public void handleTooManyRequests() {
    }

    @ExceptionHandler(MercadoPagoGatewayException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public void handleMercadoPagoGateway() {
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void handleDataIntegrityViolation() {
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void handleOptimisticLockingFailure() {
    }
}
