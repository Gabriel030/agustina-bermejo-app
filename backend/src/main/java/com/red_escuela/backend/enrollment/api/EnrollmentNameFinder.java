package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.EnrollmentNameRow;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class EnrollmentNameFinder {
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;

    public EnrollmentNameFinder(ProgramEnrollmentRepository programEnrollmentRepository, CourseEnrollmentRepository courseEnrollmentRepository) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    @Transactional(readOnly = true)
    public Map<Long, String> findProgramNamesByProgramEnrollmentId(Collection<Long> programEnrollmentIds) {
        return namesOf(programEnrollmentIds, programEnrollmentRepository::findNameRows);
    }

    @Transactional(readOnly = true)
    public Map<Long, String> findCourseNamesByCourseEnrollmentId(Collection<Long> courseEnrollmentIds) {
        return namesOf(courseEnrollmentIds, courseEnrollmentRepository::findNameRows);
    }

    private Map<Long, String> namesOf(Collection<Long> enrollmentIds, java.util.function.Function<Collection<Long>, List<EnrollmentNameRow>> rowsQuery) {
        Map<Long, String> names = new HashMap<>();
        if (enrollmentIds.isEmpty()) {
            return names;
        }
        rowsQuery.apply(enrollmentIds).forEach(row -> names.put(row.getEnrollmentId(), row.getName()));
        return names;
    }
}
