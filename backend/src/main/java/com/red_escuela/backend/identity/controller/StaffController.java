package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.dto.CreateStaffRequest;
import com.red_escuela.backend.identity.dto.StaffRecordResponse;
import com.red_escuela.backend.identity.dto.StaffResponse;
import com.red_escuela.backend.identity.dto.UpdateStaffRequest;
import com.red_escuela.backend.identity.service.StaffRoleAssignmentPolicy;
import com.red_escuela.backend.identity.usecase.staff.CreateStaffUseCase;
import com.red_escuela.backend.identity.usecase.staff.GetStaffRecordUseCase;
import com.red_escuela.backend.identity.usecase.staff.ListStaffCommand;
import com.red_escuela.backend.identity.usecase.staff.ListStaffUseCase;
import com.red_escuela.backend.identity.usecase.staff.ReactivateStaffUseCase;
import com.red_escuela.backend.identity.usecase.staff.ResetStaffPasswordUseCase;
import com.red_escuela.backend.identity.usecase.staff.StaffUseCaseResult;
import com.red_escuela.backend.identity.usecase.staff.UpdateStaffCommand;
import com.red_escuela.backend.identity.usecase.staff.UpdateStaffUseCase;
import com.red_escuela.backend.identity.usecase.staff.WithdrawStaffUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/staff")
public class StaffController {

    private final CreateStaffUseCase createStaffUseCase;
    private final UpdateStaffUseCase updateStaffUseCase;
    private final ListStaffUseCase listStaffUseCase;
    private final GetStaffRecordUseCase getStaffRecordUseCase;
    private final WithdrawStaffUseCase withdrawStaffUseCase;
    private final ReactivateStaffUseCase reactivateStaffUseCase;
    private final ResetStaffPasswordUseCase resetStaffPasswordUseCase;
    private final StaffRoleAssignmentPolicy staffRoleAssignmentPolicy;

    public StaffController(CreateStaffUseCase createStaffUseCase,
                           UpdateStaffUseCase updateStaffUseCase,
                           ListStaffUseCase listStaffUseCase,
                           GetStaffRecordUseCase getStaffRecordUseCase,
                           WithdrawStaffUseCase withdrawStaffUseCase,
                           ReactivateStaffUseCase reactivateStaffUseCase,
                           ResetStaffPasswordUseCase resetStaffPasswordUseCase,
                           StaffRoleAssignmentPolicy staffRoleAssignmentPolicy) {
        this.createStaffUseCase = createStaffUseCase;
        this.updateStaffUseCase = updateStaffUseCase;
        this.listStaffUseCase = listStaffUseCase;
        this.getStaffRecordUseCase = getStaffRecordUseCase;
        this.withdrawStaffUseCase = withdrawStaffUseCase;
        this.reactivateStaffUseCase = reactivateStaffUseCase;
        this.resetStaffPasswordUseCase = resetStaffPasswordUseCase;
        this.staffRoleAssignmentPolicy = staffRoleAssignmentPolicy;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public StaffResponse createStaff(@Valid @RequestBody CreateStaffRequest request) {
        StaffUseCaseResult result = createStaffUseCase.create(request);
        return StaffResponse.from(result.person(), result.role());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public StaffResponse updateStaff(@PathVariable Long id, @Valid @RequestBody UpdateStaffRequest request) {
        StaffUseCaseResult result = updateStaffUseCase.update(new UpdateStaffCommand(id, request));
        return StaffResponse.from(result.person(), result.role());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<StaffResponse> listStaff(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) StatusType status,
            @RequestParam(required = false) String search) {
        return listStaffUseCase.list(new ListStaffCommand(role, status, search))
                .stream()
                .map(p -> StaffResponse.from(p, staffRoleAssignmentPolicy.resolveStaffRole(p)))
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public StaffRecordResponse getRecord(@PathVariable Long id) {
        var person = getStaffRecordUseCase.getRecord(id);
        return StaffRecordResponse.from(person, staffRoleAssignmentPolicy.resolveStaffRole(person));
    }

    @PostMapping("/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public void withdraw(@PathVariable Long id) {
        withdrawStaffUseCase.withdraw(id);
    }

    @PostMapping("/{id}/reactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public void reactivate(@PathVariable Long id) {
        reactivateStaffUseCase.reactivate(id);
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public void resetPassword(@PathVariable Long id) {
        resetStaffPasswordUseCase.resetPassword(id);
    }
}
