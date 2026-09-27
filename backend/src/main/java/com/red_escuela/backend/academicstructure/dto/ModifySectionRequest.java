package com.red_escuela.backend.academicstructure.dto;

import java.util.List;

public record ModifySectionRequest(List<ScheduleSlotRequest> slots) {}
