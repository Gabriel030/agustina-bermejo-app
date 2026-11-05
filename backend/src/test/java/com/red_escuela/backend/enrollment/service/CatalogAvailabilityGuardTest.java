package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.exception.CourseNotAvailableException;
import com.red_escuela.backend.enrollment.exception.ProgramNotAvailableException;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogAvailabilityGuardTest {

    private final CatalogAvailabilityGuard guard = new CatalogAvailabilityGuard();

    @Test
    void activeVisibleProgramIsAccepted() {
        assertThatCode(() -> guard.requireProgramListed(program(StatusType.ACTIVE, true))).doesNotThrowAnyException();
    }

    @Test
    void programWithNullStatusCountsAsListed() {
        assertThatCode(() -> guard.requireProgramListed(program(null, true))).doesNotThrowAnyException();
    }

    @Test
    void hiddenProgramIsRejected() {
        assertThatThrownBy(() -> guard.requireProgramListed(program(StatusType.ACTIVE, false)))
                .isInstanceOf(ProgramNotAvailableException.class);
    }

    @Test
    void inactiveProgramIsRejected() {
        assertThatThrownBy(() -> guard.requireProgramListed(program(StatusType.INACTIVE, true)))
                .isInstanceOf(ProgramNotAvailableException.class);
    }

    @Test
    void deletedProgramIsRejected() {
        assertThatThrownBy(() -> guard.requireProgramListed(program(StatusType.DELETED, true)))
                .isInstanceOf(ProgramNotAvailableException.class);
    }

    @Test
    void activeVisibleCourseIsAccepted() {
        assertThatCode(() -> guard.requireCourseListed(course(StatusType.ACTIVE, true))).doesNotThrowAnyException();
    }

    @Test
    void courseWithNullStatusCountsAsListed() {
        assertThatCode(() -> guard.requireCourseListed(course(null, true))).doesNotThrowAnyException();
    }

    @Test
    void hiddenCourseIsRejected() {
        assertThatThrownBy(() -> guard.requireCourseListed(course(StatusType.ACTIVE, false)))
                .isInstanceOf(CourseNotAvailableException.class);
    }

    @Test
    void inactiveCourseIsRejected() {
        assertThatThrownBy(() -> guard.requireCourseListed(course(StatusType.INACTIVE, true)))
                .isInstanceOf(CourseNotAvailableException.class);
    }

    private Program program(StatusType status, boolean visible) {
        Program program = new Program();
        program.setStatus(status);
        program.setVisibleInCatalog(visible);
        return program;
    }

    private Course course(StatusType status, boolean visible) {
        Course course = new Course();
        course.setIsActive(status);
        course.setVisibleInCatalog(visible);
        return course;
    }
}
