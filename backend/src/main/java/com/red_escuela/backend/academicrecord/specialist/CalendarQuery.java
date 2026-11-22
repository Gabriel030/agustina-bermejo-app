package com.red_escuela.backend.academicrecord.specialist;

import com.red_escuela.backend.academicstructure.api.HolidayView;
import com.red_escuela.backend.enrollment.api.ActiveSectionView;

import java.time.LocalDate;
import java.util.List;

public record CalendarQuery(Long studentId, LocalDate from, LocalDate to,
                            List<ActiveSectionView> activeSections, List<HolidayView> holidays) {
}
