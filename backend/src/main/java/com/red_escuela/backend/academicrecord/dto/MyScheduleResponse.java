package com.red_escuela.backend.academicrecord.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(name = "MyScheduleResponse")
public record MyScheduleResponse(OffsetDateTime generatedAt, List<MyScheduleItemResponse> items) {
}
