package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.ChangeSectionCandidateResponse;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class OwnChangeSectionCandidatesFinder {

    private final SubjectSectionEnrollmentService subjectSectionEnrollmentService;
    private final ChangeSectionCandidatesFinder changeSectionCandidatesFinder;

    public OwnChangeSectionCandidatesFinder(SubjectSectionEnrollmentService subjectSectionEnrollmentService,
                                            ChangeSectionCandidatesFinder changeSectionCandidatesFinder) {
        this.subjectSectionEnrollmentService = subjectSectionEnrollmentService;
        this.changeSectionCandidatesFinder = changeSectionCandidatesFinder;
    }

    public List<ChangeSectionCandidateResponse> find(Long studentId, Long sectionEnrollmentId) {
        if (!subjectSectionEnrollmentService.get(sectionEnrollmentId).getStudent().getId().equals(studentId)) {
            throw new SectionEnrollmentNotFoundException(sectionEnrollmentId);
        }
        return changeSectionCandidatesFinder.findCandidates(sectionEnrollmentId);
    }
}
