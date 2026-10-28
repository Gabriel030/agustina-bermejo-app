package com.red_escuela.backend.billing.discount.controller;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.LabeledStudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountSearch;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus;
import com.red_escuela.backend.billing.discount.dto.GrantStudentDiscountRequest;
import com.red_escuela.backend.billing.discount.dto.RevokeStudentDiscountRequest;
import com.red_escuela.backend.billing.discount.dto.StudentDiscountPendingAuthorizationResponse;
import com.red_escuela.backend.billing.discount.dto.StudentDiscountGrantPreviewResponse;
import com.red_escuela.backend.billing.discount.dto.StudentDiscountPageResponse;
import com.red_escuela.backend.billing.discount.dto.StudentDiscountResponse;
import com.red_escuela.backend.billing.discount.usecase.GrantStudentDiscountUseCase;
import com.red_escuela.backend.billing.discount.usecase.ListMyStudentDiscountsUseCase;
import com.red_escuela.backend.billing.discount.usecase.ListStudentDiscountsOfStudentUseCase;
import com.red_escuela.backend.billing.discount.usecase.ListStudentDiscountsUseCase;
import com.red_escuela.backend.billing.discount.usecase.PreviewStudentDiscountGrantUseCase;
import com.red_escuela.backend.billing.discount.usecase.RevokeStudentDiscountUseCase;
import com.red_escuela.backend.billing.discount.usecase.SearchStudentDiscountsUseCase;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class StudentDiscountController {
    private final GrantStudentDiscountUseCase grantUseCase;
    private final RevokeStudentDiscountUseCase revokeUseCase;
    private final ListStudentDiscountsUseCase listUseCase;
    private final ListMyStudentDiscountsUseCase listMyUseCase;
    private final ListStudentDiscountsOfStudentUseCase listOfStudentUseCase;
    private final SearchStudentDiscountsUseCase searchUseCase;
    private final PreviewStudentDiscountGrantUseCase previewUseCase;

    public StudentDiscountController(GrantStudentDiscountUseCase grantUseCase, RevokeStudentDiscountUseCase revokeUseCase, ListStudentDiscountsUseCase listUseCase,
                                     ListMyStudentDiscountsUseCase listMyUseCase, ListStudentDiscountsOfStudentUseCase listOfStudentUseCase,
                                     SearchStudentDiscountsUseCase searchUseCase, PreviewStudentDiscountGrantUseCase previewUseCase) {
        this.grantUseCase = grantUseCase;
        this.revokeUseCase = revokeUseCase;
        this.listUseCase = listUseCase;
        this.listMyUseCase = listMyUseCase;
        this.listOfStudentUseCase = listOfStudentUseCase;
        this.searchUseCase = searchUseCase;
        this.previewUseCase = previewUseCase;
    }

    @PostMapping("/program-enrollments/{id}/student-discounts")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "El director otorgó el descuento", content = @Content(schema = @Schema(implementation = StudentDiscountResponse.class))),
            @ApiResponse(responseCode = "202", description = "El administrativo dejó un pedido pendiente de aprobación", content = @Content(schema = @Schema(implementation = StudentDiscountPendingAuthorizationResponse.class)))})
    public ResponseEntity<?> grantForProgramEnrollment(@PathVariable Long id, @Valid @RequestBody GrantStudentDiscountRequest request) {
        return responseOf(grantUseCase.grantStudentDiscount(EnrollmentReference.ofProgram(id), request.toGrantRequest()));
    }

    @PostMapping("/course-enrollments/{id}/student-discounts")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "El director otorgó el descuento", content = @Content(schema = @Schema(implementation = StudentDiscountResponse.class))),
            @ApiResponse(responseCode = "202", description = "El administrativo dejó un pedido pendiente de aprobación", content = @Content(schema = @Schema(implementation = StudentDiscountPendingAuthorizationResponse.class)))})
    public ResponseEntity<?> grantForCourseEnrollment(@PathVariable Long id, @Valid @RequestBody GrantStudentDiscountRequest request) {
        return responseOf(grantUseCase.grantStudentDiscount(EnrollmentReference.ofCourse(id), request.toGrantRequest()));
    }

    @GetMapping("/program-enrollments/{id}/student-discounts")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<StudentDiscountResponse> listForProgramEnrollment(@PathVariable Long id) {
        return staffResponsesOf(listUseCase.listStudentDiscounts(EnrollmentReference.ofProgram(id)));
    }

    @GetMapping("/course-enrollments/{id}/student-discounts")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<StudentDiscountResponse> listForCourseEnrollment(@PathVariable Long id) {
        return staffResponsesOf(listUseCase.listStudentDiscounts(EnrollmentReference.ofCourse(id)));
    }

    @GetMapping("/me/student-discounts")
    @PreAuthorize("hasRole('ALUMNO')")
    public List<StudentDiscountResponse> listMyStudentDiscounts() {
        return listMyUseCase.listMyStudentDiscounts().stream().map(item -> StudentDiscountResponse.forStudent(item.discountWithUsage(), item.labels())).toList();
    }

    @GetMapping("/student-discounts")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public StudentDiscountPageResponse searchStudentDiscounts(@RequestParam(required = false) StudentDiscountStatus status, @RequestParam(required = false) DiscountType type,
                                                              @RequestParam(required = false) DiscountDefinitionType definitionType, @RequestParam(required = false) Long discountDefinitionId,
                                                              @RequestParam(required = false) Long studentId, @RequestParam(required = false) Long programEnrollmentId,
                                                              @RequestParam(required = false) Long courseEnrollmentId, @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return StudentDiscountPageResponse.from(searchUseCase.searchStudentDiscounts(
                new StudentDiscountSearch(status, type, definitionType, discountDefinitionId, studentId, programEnrollmentId, courseEnrollmentId), page, size));
    }

    @GetMapping("/students/{studentId}/student-discounts")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<StudentDiscountResponse> listForStudent(@PathVariable Long studentId) {
        return staffResponsesOf(listOfStudentUseCase.listStudentDiscountsOfStudent(studentId));
    }

    @PostMapping("/program-enrollments/{id}/student-discounts/preview")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public StudentDiscountGrantPreviewResponse previewForProgramEnrollment(@PathVariable Long id, @Valid @RequestBody GrantStudentDiscountRequest request) {
        return StudentDiscountGrantPreviewResponse.from(previewUseCase.previewStudentDiscountGrant(EnrollmentReference.ofProgram(id), request.toGrantRequest()));
    }

    @PostMapping("/course-enrollments/{id}/student-discounts/preview")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public StudentDiscountGrantPreviewResponse previewForCourseEnrollment(@PathVariable Long id, @Valid @RequestBody GrantStudentDiscountRequest request) {
        return StudentDiscountGrantPreviewResponse.from(previewUseCase.previewStudentDiscountGrant(EnrollmentReference.ofCourse(id), request.toGrantRequest()));
    }

    @PostMapping("/student-discounts/{id}/revocation")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "El director revocó el descuento", content = @Content(schema = @Schema(implementation = StudentDiscountResponse.class))),
            @ApiResponse(responseCode = "202", description = "El administrativo dejó un pedido de revocación pendiente; vence a fin de mes si nadie lo decide", content = @Content(schema = @Schema(implementation = StudentDiscountPendingAuthorizationResponse.class)))})
    public ResponseEntity<?> revokeStudentDiscount(@PathVariable Long id, @RequestBody RevokeStudentDiscountRequest request) {
        return responseOf(revokeUseCase.revokeStudentDiscount(id, request.reason()));
    }

    private List<StudentDiscountResponse> staffResponsesOf(List<LabeledStudentDiscount> discounts) {
        return discounts.stream().map(item -> StudentDiscountResponse.forStaff(item.discountWithUsage(), item.labels())).toList();
    }

    private ResponseEntity<?> responseOf(BillingOperationOutcome<StudentDiscount> outcome) {
        return switch (outcome) {
            case BillingOperationOutcome.Processed<StudentDiscount> processed ->
                    ResponseEntity.status(HttpStatus.CREATED).body(StudentDiscountResponse.from(processed.result()));
            case BillingOperationOutcome.AwaitingAuthorization<StudentDiscount> awaiting ->
                    ResponseEntity.status(HttpStatus.ACCEPTED).body(new StudentDiscountPendingAuthorizationResponse(awaiting.authorizationRequestId()));
        };
    }
}
