package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.exception.InstructorNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.exception.NotOwnSectionException;
import org.springframework.stereotype.Service;

@Service
public class AttendanceSectionAccess {

    private final SubjectSectionInstructorRepository subjectSectionInstructorRepository;
    private final CourseSubjectSectionInstructorRepository courseSubjectSectionInstructorRepository;
    private final InstructorRepository instructorRepository;

    public AttendanceSectionAccess(SubjectSectionInstructorRepository subjectSectionInstructorRepository,
                                    CourseSubjectSectionInstructorRepository courseSubjectSectionInstructorRepository,
                                    InstructorRepository instructorRepository) {
        this.subjectSectionInstructorRepository = subjectSectionInstructorRepository;
        this.courseSubjectSectionInstructorRepository = courseSubjectSectionInstructorRepository;
        this.instructorRepository = instructorRepository;
    }

    public boolean isOwnProgramSection(Long sectionId, Long instructorId) {
        return subjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId);
    }

    public boolean isOwnCourseSection(Long sectionId, Long instructorId) {
        return courseSubjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId);
    }

    public void requireOwnProgramSection(Long sectionId, Long instructorId) {
        if (!isOwnProgramSection(sectionId, instructorId)) {
            throw new NotOwnSectionException(instructorId, sectionId);
        }
    }

    public void requireOwnCourseSection(Long sectionId, Long instructorId) {
        if (!isOwnCourseSection(sectionId, instructorId)) {
            throw new NotOwnSectionException(instructorId, sectionId);
        }
    }

    public void requireOwnProgramSectionIfInstructorGiven(Long sectionId, Long instructorIdIfDocente) {
        if (instructorIdIfDocente != null && !isOwnProgramSection(sectionId, instructorIdIfDocente)) {
            throw new NotOwnSectionException(instructorIdIfDocente, sectionId);
        }
    }

    public void requireOwnCourseSectionIfInstructorGiven(Long sectionId, Long instructorIdIfDocente) {
        if (instructorIdIfDocente != null && !isOwnCourseSection(sectionId, instructorIdIfDocente)) {
            throw new NotOwnSectionException(instructorIdIfDocente, sectionId);
        }
    }

    public Instructor resolveInstructor(Long instructorId) {
        return instructorRepository.findById(instructorId)
                .orElseThrow(() -> new InstructorNotFoundException(instructorId));
    }
}
