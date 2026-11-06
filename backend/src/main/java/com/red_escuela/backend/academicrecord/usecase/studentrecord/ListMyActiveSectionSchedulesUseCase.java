package com.red_escuela.backend.academicrecord.usecase.studentrecord;

import com.red_escuela.backend.academicrecord.dto.MyScheduleResponse;

public interface ListMyActiveSectionSchedulesUseCase {
    MyScheduleResponse list(Long studentId);

    MyScheduleResponse listEmpty();
}
