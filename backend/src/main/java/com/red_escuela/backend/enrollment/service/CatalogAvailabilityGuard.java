package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.exception.CourseNotAvailableException;
import com.red_escuela.backend.enrollment.exception.ProgramNotAvailableException;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.stereotype.Component;

@Component
public class CatalogAvailabilityGuard {

    public void requireProgramListed(Program program) {
        if (!isListed(program.getStatus(), program.isVisibleInCatalog())) {
            throw new ProgramNotAvailableException(program.getId());
        }
    }

    public void requireCourseListed(Course course) {
        if (!isListed(course.getIsActive(), course.isVisibleInCatalog())) {
            throw new CourseNotAvailableException(course.getId());
        }
    }

    private boolean isListed(StatusType status, boolean visibleInCatalog) {
        return (status == null || status == StatusType.ACTIVE) && visibleInCatalog;
    }
}
