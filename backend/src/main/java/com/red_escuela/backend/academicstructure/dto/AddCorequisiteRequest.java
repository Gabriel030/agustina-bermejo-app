package com.red_escuela.backend.academicstructure.dto;

import jakarta.validation.constraints.NotNull;

public record AddCorequisiteRequest(@NotNull Long corequisiteProgramSubjectId) {}
