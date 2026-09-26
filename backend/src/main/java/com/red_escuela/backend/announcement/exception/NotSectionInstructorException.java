package com.red_escuela.backend.announcement.exception;

public class NotSectionInstructorException extends RuntimeException {

    public NotSectionInstructorException(Long sectionId) {
        super("Current instructor does not teach section " + sectionId);
    }
}
