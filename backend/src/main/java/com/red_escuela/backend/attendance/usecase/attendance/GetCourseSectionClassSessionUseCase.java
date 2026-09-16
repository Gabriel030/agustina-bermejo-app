package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.entity.ClassSession;

public interface GetCourseSectionClassSessionUseCase {
    ClassSession get(GetCourseSectionClassSessionCommand command);
}
