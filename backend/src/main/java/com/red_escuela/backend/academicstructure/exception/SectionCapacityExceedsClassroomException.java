package com.red_escuela.backend.academicstructure.exception;

public class SectionCapacityExceedsClassroomException extends RuntimeException {
    public SectionCapacityExceedsClassroomException(Long classroomId, Integer classroomCapacity, Integer sectionCapacity) {
        super("El cupo " + sectionCapacity + " supera la capacidad del aula " + classroomId + " (" + classroomCapacity + ")");
    }
}
