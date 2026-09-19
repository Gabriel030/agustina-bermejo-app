package com.red_escuela.backend.academicstructure.usecase.classroom;

import com.red_escuela.backend.academicstructure.dto.ClassroomResponse;
import com.red_escuela.backend.academicstructure.dto.CreateClassroomRequest;

public interface CreateClassroomUseCase {
    ClassroomResponse create(CreateClassroomRequest request);
}
