package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.identity.dto.ReactivateStudentRequest;
import com.red_escuela.backend.identity.dto.StudentRecordResponse;
import com.red_escuela.backend.identity.dto.StudentResponse;
import com.red_escuela.backend.identity.dto.UpdateStudentRequest;
import com.red_escuela.backend.identity.dto.WithdrawStudentRequest;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.pagination.PageResponse;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.usecase.student.GetStudentRecordUseCase;
import com.red_escuela.backend.identity.usecase.student.ListStudentsCommand;
import com.red_escuela.backend.identity.usecase.student.ListStudentsUseCase;
import com.red_escuela.backend.identity.usecase.student.ReactivateStudentCommand;
import com.red_escuela.backend.identity.usecase.student.ReactivateStudentUseCase;
import com.red_escuela.backend.identity.usecase.student.ResetStudentPasswordUseCase;
import com.red_escuela.backend.identity.usecase.student.UpdateStudentCommand;
import com.red_escuela.backend.identity.usecase.student.UpdateStudentUseCase;
import com.red_escuela.backend.identity.usecase.student.WithdrawStudentCommand;
import com.red_escuela.backend.identity.usecase.student.WithdrawStudentUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/students")
public class StudentController {
    private final ListStudentsUseCase listStudentsUseCase;
    private final GetStudentRecordUseCase getStudentRecordUseCase;
    private final WithdrawStudentUseCase withdrawStudentUseCase;
    private final ResetStudentPasswordUseCase resetStudentPasswordUseCase;
    private final ReactivateStudentUseCase reactivateStudentUseCase;
    private final UpdateStudentUseCase updateStudentUseCase;

    public StudentController(ListStudentsUseCase listStudentsUseCase, GetStudentRecordUseCase getStudentRecordUseCase,
                              WithdrawStudentUseCase withdrawStudentUseCase, ResetStudentPasswordUseCase resetStudentPasswordUseCase,
                              ReactivateStudentUseCase reactivateStudentUseCase, UpdateStudentUseCase updateStudentUseCase) {
        this.listStudentsUseCase = listStudentsUseCase;
        this.getStudentRecordUseCase = getStudentRecordUseCase;
        this.withdrawStudentUseCase = withdrawStudentUseCase;
        this.resetStudentPasswordUseCase = resetStudentPasswordUseCase;
        this.reactivateStudentUseCase = reactivateStudentUseCase;
        this.updateStudentUseCase = updateStudentUseCase;
    }

    @GetMapping
    @RequiresPermission(code = "identity.read_students", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public PageResponse<StudentResponse> list(
            @RequestParam(required = false) StatusType status,
            @RequestParam(required = false) StudentOnboardingStatus onboardingStatus,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return listStudentsUseCase.list(new ListStudentsCommand(status, onboardingStatus == null ? null : onboardingStatus.name(), search, page, size, seesWithdrawalReason()));
    }

    @GetMapping("/{id}")
    @RequiresPermission(code = "identity.read_students", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public StudentRecordResponse getRecord(@PathVariable Long id) {
        return StudentRecordResponse.from(getStudentRecordUseCase.getRecord(id), seesWithdrawalReason());
    }

    @PutMapping("/{id}")
    @RequiresPermission(code = "identity.withdraw_student", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public StudentRecordResponse update(@PathVariable Long id, @Valid @RequestBody UpdateStudentRequest request) {
        return updateStudentUseCase.update(new UpdateStudentCommand(id, request, seesWithdrawalReason()));
    }

    @PostMapping("/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(code = "identity.withdraw_student", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public void withdraw(@PathVariable Long id, @Valid @RequestBody WithdrawStudentRequest request) {
        withdrawStudentUseCase.withdraw(new WithdrawStudentCommand(id, request));
    }

    @PostMapping("/{id}/reactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(code = "identity.reactivate_student", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public void reactivate(@PathVariable Long id, @Valid @RequestBody ReactivateStudentRequest request) {
        reactivateStudentUseCase.reactivate(new ReactivateStudentCommand(id, request));
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequiresPermission(code = "identity.reset_student_password", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO")
    public void resetPassword(@PathVariable Long id) {
        resetStudentPasswordUseCase.resetPassword(id);
    }

    private boolean seesWithdrawalReason() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> "ROLE_DIRECTOR".equals(authority.getAuthority())
                        || "ROLE_ADMINISTRATIVO".equals(authority.getAuthority()));
    }
}
