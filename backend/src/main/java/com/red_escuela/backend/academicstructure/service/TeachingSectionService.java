package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.TeachingSectionResponse;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionInstructorRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TeachingSectionService {

    private final SubjectSectionInstructorRepository subjectSectionInstructorRepository;
    private final CourseSubjectSectionInstructorRepository courseSubjectSectionInstructorRepository;

    public TeachingSectionService(
            SubjectSectionInstructorRepository subjectSectionInstructorRepository,
            CourseSubjectSectionInstructorRepository courseSubjectSectionInstructorRepository) {
        this.subjectSectionInstructorRepository = subjectSectionInstructorRepository;
        this.courseSubjectSectionInstructorRepository = courseSubjectSectionInstructorRepository;
    }

    public List<TeachingSectionResponse> listByInstructor(Long instructorId) {
        List<TeachingSectionResponse> sections = new ArrayList<>();
        subjectSectionInstructorRepository.findByInstructorId(instructorId).stream()
                .map(link -> link.getSection())
                .filter(section -> section.isOfferingApproved())
                .forEach(section -> sections.add(TeachingSectionResponse.fromSubjectSection(section)));
        courseSubjectSectionInstructorRepository.findByInstructorId(instructorId).stream()
                .map(link -> link.getSection())
                .filter(section -> section.isOfferingApproved())
                .forEach(section -> sections.add(TeachingSectionResponse.fromCourseSubjectSection(section)));
        return sections;
    }
}
