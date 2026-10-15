package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.billing.dto.AdminEnrollmentBillingStatusResponse;
import com.red_escuela.backend.billing.usecase.invoice.GetStudentBillingOverviewUseCase;
import com.red_escuela.backend.enrollment.dto.StudentDocumentResponse;
import com.red_escuela.backend.identity.dto.StudentRecordResponse;
import com.red_escuela.backend.identity.dto.StudentResponse;
import com.red_escuela.backend.identity.dto.WithdrawStudentRequest;
import com.red_escuela.backend.enrollment.repository.StudentDocumentRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.usecase.student.GetStudentRecordUseCase;
import com.red_escuela.backend.identity.usecase.student.ListStudentsCommand;
import com.red_escuela.backend.identity.usecase.student.ListStudentsUseCase;
import com.red_escuela.backend.identity.usecase.student.ResetStudentPasswordUseCase;
import com.red_escuela.backend.identity.usecase.student.WithdrawStudentCommand;
import com.red_escuela.backend.identity.usecase.student.WithdrawStudentUseCase;
import com.red_escuela.backend.identity.repository.StudentRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/students")
public class StudentController {
    private final ListStudentsUseCase listStudentsUseCase;
    private final GetStudentRecordUseCase getStudentRecordUseCase;
    private final WithdrawStudentUseCase withdrawStudentUseCase;
    private final ResetStudentPasswordUseCase resetStudentPasswordUseCase;
    private final GetStudentBillingOverviewUseCase getStudentBillingOverviewUseCase;
    private final StudentRepository studentRepository;
    private final StudentDocumentRepository studentDocumentRepository;

    public StudentController(ListStudentsUseCase listStudentsUseCase, GetStudentRecordUseCase getStudentRecordUseCase,
                              WithdrawStudentUseCase withdrawStudentUseCase, ResetStudentPasswordUseCase resetStudentPasswordUseCase,
                              GetStudentBillingOverviewUseCase getStudentBillingOverviewUseCase, StudentRepository studentRepository,
                              StudentDocumentRepository studentDocumentRepository) {
        this.listStudentsUseCase = listStudentsUseCase;
        this.getStudentRecordUseCase = getStudentRecordUseCase;
        this.withdrawStudentUseCase = withdrawStudentUseCase;
        this.resetStudentPasswordUseCase = resetStudentPasswordUseCase;
        this.getStudentBillingOverviewUseCase = getStudentBillingOverviewUseCase;
        this.studentRepository = studentRepository;
        this.studentDocumentRepository = studentDocumentRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<StudentResponse> list(
            @RequestParam(required = false) StatusType status,
            @RequestParam(required = false) StudentOnboardingStatus onboardingStatus,
            @RequestParam(required = false) String search) {
        return listStudentsUseCase.list(new ListStudentsCommand(status, onboardingStatus, search))
                .stream().map(StudentResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public StudentRecordResponse getRecord(@PathVariable Long id) {
        return StudentRecordResponse.from(getStudentRecordUseCase.getRecord(id));
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<StudentDocumentResponse> getDocuments(@PathVariable Long id) {
        if (!studentRepository.existsById(id)) throw new StudentNotFoundException(id);
        return studentDocumentRepository.findByStudentId(id).stream()
                .map(StudentDocumentResponse::from).toList();
    }

    @PostMapping("/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public void withdraw(@PathVariable Long id, @Valid @RequestBody WithdrawStudentRequest request) {
        withdrawStudentUseCase.withdraw(new WithdrawStudentCommand(id, request));
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void resetPassword(@PathVariable Long id) {
        resetStudentPasswordUseCase.resetPassword(id);
    }

    @GetMapping("/{id}/billing-overview")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<AdminEnrollmentBillingStatusResponse> billingOverview(@PathVariable Long id) {
        return getStudentBillingOverviewUseCase.get(id);
    }



}
