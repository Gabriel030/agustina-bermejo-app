package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.CourseSubjectNotFoundException;
import com.red_escuela.backend.academicstructure.exception.InstructorNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SubjectNotFoundException;
import com.red_escuela.backend.academicstructure.exception.TermNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectRepository;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class AcademicReferenceFinder {

    private final SubjectRepository subjectRepository;
    private final CourseSubjectRepository courseSubjectRepository;
    private final TermRepository termRepository;
    private final InstructorRepository instructorRepository;

    public AcademicReferenceFinder(SubjectRepository subjectRepository,
                                   CourseSubjectRepository courseSubjectRepository,
                                   TermRepository termRepository,
                                   InstructorRepository instructorRepository) {
        this.subjectRepository = subjectRepository;
        this.courseSubjectRepository = courseSubjectRepository;
        this.termRepository = termRepository;
        this.instructorRepository = instructorRepository;
    }

    public Subject findSubject(Long id) {
        return subjectRepository.findById(id).orElseThrow(() -> new SubjectNotFoundException(id));
    }

    public CourseSubject findCourseSubject(Long id) {
        return courseSubjectRepository.findById(id).orElseThrow(() -> new CourseSubjectNotFoundException(id));
    }

    public Term findTerm(Long id) {
        return termRepository.findById(id).orElseThrow(() -> new TermNotFoundException(id));
    }

    public Instructor findInstructor(Long id) {
        return instructorRepository.findById(id).orElseThrow(() -> new InstructorNotFoundException(id));
    }
}
