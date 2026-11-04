package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.ShiftRangeResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateShiftRangesRequest;
import com.red_escuela.backend.academicstructure.usecase.shift.ListShiftRangesUseCase;
import com.red_escuela.backend.academicstructure.usecase.shift.UpdateShiftRangesUseCase;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ShiftRangeController {

    private final ListShiftRangesUseCase listShiftRanges;
    private final UpdateShiftRangesUseCase updateShiftRanges;

    public ShiftRangeController(ListShiftRangesUseCase listShiftRanges, UpdateShiftRangesUseCase updateShiftRanges) {
        this.listShiftRanges = listShiftRanges;
        this.updateShiftRanges = updateShiftRanges;
    }

    @GetMapping("/shift-ranges")
    @PreAuthorize("isAuthenticated()")
    public List<ShiftRangeResponse> list() {
        return listShiftRanges.list();
    }

    @PutMapping("/shift-ranges")
    @PreAuthorize("hasRole('DIRECTOR')")
    public List<ShiftRangeResponse> update(@Valid @RequestBody UpdateShiftRangesRequest request) {
        return updateShiftRanges.update(request);
    }
}
