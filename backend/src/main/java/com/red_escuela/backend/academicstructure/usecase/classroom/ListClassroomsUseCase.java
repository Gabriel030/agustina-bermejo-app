package com.red_escuela.backend.academicstructure.usecase.classroom;

import com.red_escuela.backend.academicstructure.dto.ClassroomResponse;

import java.util.List;

public interface ListClassroomsUseCase {
    List<ClassroomResponse> list();
}
