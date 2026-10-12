package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class SectionCapacityExceedsClassroomException extends BadRequestException {
    public SectionCapacityExceedsClassroomException(Long classroomId, Integer classroomCapacity, Integer sectionCapacity) {
        super("El cupo " + sectionCapacity + " supera la capacidad del aula " + classroomId + " (" + classroomCapacity + ")");
    }
}
