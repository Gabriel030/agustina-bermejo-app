package com.red_escuela.backend.announcement.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class NotSectionInstructorException extends NotFoundException {

    public NotSectionInstructorException(Long sectionId) {
        super("Current instructor does not teach section " + sectionId);
    }
}
