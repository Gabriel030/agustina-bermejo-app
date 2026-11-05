package com.red_escuela.backend.academicstructure.api;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record SectionSlotView(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, String classroomName) {
}
