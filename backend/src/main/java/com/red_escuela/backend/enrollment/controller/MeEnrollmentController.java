package com.red_escuela.backend.enrollment.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.enrollment.dto.ChangeSectionRequest;
import com.red_escuela.backend.enrollment.dto.CourseEnrollmentApplicationResponse;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.enrollment.dto.CreateOwnSectionEnrollmentRequest;
import com.red_escuela.backend.enrollment.dto.CreateSectionEnrollmentRequest;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentResponse;
import com.red_escuela.backend.enrollment.dto.ProgramProgressResponse;
import com.red_escuela.backend.enrollment.dto.SectionEnrollmentResponse;
import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.InvalidSectionOverridesException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentApplicationService;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.enrollment.service.SubjectSectionEnrollmentService;
import com.red_escuela.backend.enrollment.dto.CourseTransferReceiptForm;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/me")
public class MeEnrollmentController {
    private final CurrentStudentService currentStudentService;
    private final ProgramEnrollmentService programEnrollmentService;
    private final SubjectSectionEnrollmentService subjectSectionEnrollmentService;
    private final CourseEnrollmentApplicationService courseEnrollmentApplicationService;
    private final ObjectMapper objectMapper;

    public MeEnrollmentController(
            CurrentStudentService currentStudentService,
            ProgramEnrollmentService programEnrollmentService,
            SubjectSectionEnrollmentService subjectSectionEnrollmentService,
            CourseEnrollmentApplicationService courseEnrollmentApplicationService,
            ObjectMapper objectMapper) {
        this.currentStudentService = currentStudentService;
        this.programEnrollmentService = programEnrollmentService;
        this.subjectSectionEnrollmentService = subjectSectionEnrollmentService;
        this.courseEnrollmentApplicationService = courseEnrollmentApplicationService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/course-enrollments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "BANK_TRANSFER_DETAILS_REQUIRED con la violación del campo cuando el curso es de pago, IDEMPOTENCY_KEY_INVALID, RECEIPT_INVOICE_CURRENCY_MISMATCH, RECEIPT_INVOICE_NOT_PAYABLE"),
            @ApiResponse(responseCode = "409", description = "POSSIBLE_DUPLICATE_BANK_TRANSFER, RECEIPT_MUST_COVER_OLDEST_INVOICES_FIRST, INVOICE_ALREADY_UNDER_REVIEW, LEGACY_RECEIPT_PENDING_ON_INVOICE, IDEMPOTENCY_KEY_CONFLICT")})
    public CourseEnrollmentApplicationResponse enrollInCourse(
            @RequestParam Long courseId,
            @RequestParam(required = false) String sectionOverrides,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestPart(name = "file", required = false) MultipartFile file,
            @RequestParam(name = "invoiceIds", required = false) List<Long> invoiceIds,
            @RequestParam(name = "declaredAmount", required = false) BigDecimal declaredAmount,
            @RequestParam(name = "currency", required = false) Currency currency,
            @RequestParam(name = "transferDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transferDate,
            @RequestParam(name = "operationNumber", required = false) String operationNumber,
            @RequestParam(name = "originAccount", required = false) String originAccount,
            @RequestParam(name = "holderName", required = false) String holderName,
            @RequestParam(name = "bankName", required = false) String bankName) {
        Student student = currentStudentService.resolveCurrentStudent();
        CreateCourseEnrollmentRequest request = new CreateCourseEnrollmentRequest(courseId, deserializarOverrides(sectionOverrides));
        CourseEnrollmentApplication application = courseEnrollmentApplicationService.requestWithPayment(student.getId(), request,
                new CourseTransferReceiptForm(file, invoiceIds, declaredAmount == null || currency == null ? null : Money.of(declaredAmount, currency), transferDate, operationNumber, originAccount, holderName, bankName),
                idempotencyKey);
        return CourseEnrollmentApplicationResponse.from(application);
    }

    @GetMapping("/course-enrollment-applications")
    public List<CourseEnrollmentApplicationResponse> myCourseApplications() {
        Student student = currentStudentService.resolveCurrentStudent();
        return courseEnrollmentApplicationService.listMine(student.getId()).stream()
                .map(CourseEnrollmentApplicationResponse::from).toList();
    }

    private Map<Long, Long> deserializarOverrides(String sectionOverridesJson) {
        if (sectionOverridesJson == null || sectionOverridesJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(sectionOverridesJson, new TypeReference<Map<Long, Long>>() {
            });
        } catch (JsonProcessingException e) {
            throw new InvalidSectionOverridesException(sectionOverridesJson, e);
        }
    }

    @GetMapping("/program-enrollments")
    public List<ProgramEnrollmentResponse> myProgramEnrollments() {
        Student student = currentStudentService.resolveCurrentStudent();
        return programEnrollmentService.listByStudent(student.getId()).stream()
                .map(ProgramEnrollmentResponse::from).toList();
    }

    @GetMapping("/program-enrollments/{id}/progress")
    public ProgramProgressResponse myProgress(@PathVariable Long id) {
        Student student = currentStudentService.resolveCurrentStudent();
        ProgramEnrollment enrollment = programEnrollmentService.get(id);
        requireOwner(enrollment, student);
        return programEnrollmentService.calculateProgress(id);
    }

    @PostMapping("/section-enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    public SectionEnrollmentResponse enroll(@Valid @RequestBody CreateOwnSectionEnrollmentRequest request) {
        Student student = currentStudentService.resolveCurrentStudent();
        ProgramEnrollment programEnrollment = programEnrollmentService.get(request.programEnrollmentId());
        requireOwner(programEnrollment, student);

        SubjectSectionEnrollment enrollment = subjectSectionEnrollmentService.enroll(
                request.programEnrollmentId(), new CreateSectionEnrollmentRequest(request.sectionId()));
        return SectionEnrollmentResponse.from(enrollment);
    }

    @PostMapping("/section-enrollments/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable Long id) {
        Student student = currentStudentService.resolveCurrentStudent();
        requireOwner(subjectSectionEnrollmentService.get(id), student);
        subjectSectionEnrollmentService.withdraw(id);
    }

    @PostMapping("/section-enrollments/{id}/change-section")
    public SectionEnrollmentResponse changeMySection(@PathVariable Long id, @Valid @RequestBody ChangeSectionRequest request) {
        Student student = currentStudentService.resolveCurrentStudent();
        requireOwner(subjectSectionEnrollmentService.get(id), student);
        return SectionEnrollmentResponse.from(subjectSectionEnrollmentService.changeSection(id, request));
    }

    @GetMapping("/section-enrollments")
    public List<SectionEnrollmentResponse> mine() {
        Student student = currentStudentService.resolveCurrentStudent();
        return subjectSectionEnrollmentService.listByStudent(student.getId()).stream()
                .map(enrollment -> SectionEnrollmentResponse.from(
                        enrollment,
                        subjectSectionEnrollmentService.resolveCurricularYear(enrollment),
                        subjectSectionEnrollmentService.resolveCurricularTerm(enrollment)))
                .toList();
    }

    private void requireOwner(ProgramEnrollment enrollment, Student student) {
        if (!enrollment.getStudent().getId().equals(student.getId())) {
            throw new ProgramEnrollmentNotFoundException(enrollment.getId());
        }
    }

    private void requireOwner(SubjectSectionEnrollment enrollment, Student student) {
        if (!enrollment.getStudent().getId().equals(student.getId())) {
            throw new SectionEnrollmentNotFoundException(enrollment.getId());
        }
    }
}
