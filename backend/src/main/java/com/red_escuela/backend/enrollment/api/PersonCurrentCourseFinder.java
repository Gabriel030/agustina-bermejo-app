package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.PersonCurrentCourseRow;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Transactional(readOnly = true)
public class PersonCurrentCourseFinder {

    private static final int LOOKUP_CHUNK_SIZE = 1000;
    private static final List<EnrollmentStatus> CURRENT_STATUSES =
            List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR);

    private final SubjectSectionEnrollmentRepository repository;

    public PersonCurrentCourseFinder(SubjectSectionEnrollmentRepository repository) {
        this.repository = repository;
    }

    public Map<Long, PersonCurrentCourse> findMostRecentCurrentCourses(Collection<Long> personIds) {
        Map<Long, PersonCurrentCourse> courses = new HashMap<>();
        List<Long> distinctIds = personIds.stream().distinct().toList();
        for (int from = 0; from < distinctIds.size(); from += LOOKUP_CHUNK_SIZE) {
            List<Long> chunk = distinctIds.subList(from, Math.min(from + LOOKUP_CHUNK_SIZE, distinctIds.size()));
            for (PersonCurrentCourseRow row : repository.findCurrentCourseRowsByPersonIdIn(chunk, CURRENT_STATUSES)) {
                courses.putIfAbsent(row.personId(), new PersonCurrentCourse(row.programName(), row.subjectName(), row.sectionName()));
            }
        }
        return courses;
    }
}
