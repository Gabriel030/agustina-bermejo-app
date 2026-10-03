package com.red_escuela.backend.announcement.exception;

import com.red_escuela.backend.shared.exception.ForbiddenException;

public class NotSectionInstructorException extends ForbiddenException {

    public NotSectionInstructorException(Long sectionId) {
        super("Current instructor does not teach section " + sectionId);
    }
}
