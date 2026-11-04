package com.red_escuela.backend.academicstructure.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateShiftRangesRequest(
        @NotEmpty @Size(max = 3) List<@Valid ShiftRangeRequest> ranges
) {}
