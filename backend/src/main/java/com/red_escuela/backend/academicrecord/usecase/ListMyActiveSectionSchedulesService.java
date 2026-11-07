package com.red_escuela.backend.academicrecord.usecase;

import com.red_escuela.backend.academicrecord.dto.MyScheduleItemResponse;
import com.red_escuela.backend.academicrecord.dto.MyScheduleResponse;
import com.red_escuela.backend.academicrecord.specialist.AcademicRecordCourseSectionEnrollmentFinder;
import com.red_escuela.backend.shared.UseCase;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@UseCase
public class ListMyActiveSectionSchedulesService implements ListMyActiveSectionSchedulesUseCase {

    private final AcademicRecordCourseSectionEnrollmentFinder courseSectionEnrollmentFinder;
    private final Clock clock;

    public ListMyActiveSectionSchedulesService(
            AcademicRecordCourseSectionEnrollmentFinder courseSectionEnrollmentFinder, Clock clock) {
        this.courseSectionEnrollmentFinder = courseSectionEnrollmentFinder;
        this.clock = clock;
    }

    @Override
    public MyScheduleResponse list(Long studentId) {
        return new MyScheduleResponse(OffsetDateTime.now(clock), courseSectionEnrollmentFinder.listActiveScheduleOfStudent(studentId));
    }

    @Override
    public MyScheduleResponse listEmpty() {
        return new MyScheduleResponse(OffsetDateTime.now(clock), List.<MyScheduleItemResponse>of());
    }
}
