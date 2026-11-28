package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.EndedSection;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@Transactional(readOnly = true)
public class EndedSectionFinder {

    private final SubjectSectionRepository subjectSectionRepository;
    private final CourseSubjectSectionRepository courseSectionRepository;

    public EndedSectionFinder(SubjectSectionRepository subjectSectionRepository,
            CourseSubjectSectionRepository courseSectionRepository) {
        this.subjectSectionRepository = subjectSectionRepository;
        this.courseSectionRepository = courseSectionRepository;
    }

    public List<EndedSectionRef> findActiveSectionsEndedBetween(LocalDate fromInclusive, LocalDate untilInclusive) {
        var from = fromInclusive.atStartOfDay();
        var until = untilInclusive.plusDays(1).atStartOfDay();
        List<EndedSectionRef> refs = new ArrayList<>();
        subjectSectionRepository.findEndedByStatus(StatusType.ACTIVE, from, until)
                .forEach(section -> refs.add(toRef(SectionKind.PROGRAM, section)));
        courseSectionRepository.findEndedByStatus(StatusType.ACTIVE, from, until)
                .forEach(section -> refs.add(toRef(SectionKind.COURSE, section)));
        refs.sort(Comparator.comparing(EndedSectionRef::termEnd).thenComparing(EndedSectionRef::kind).thenComparing(EndedSectionRef::sectionId));
        return refs;
    }

    private EndedSectionRef toRef(SectionKind kind, EndedSection section) {
        return new EndedSectionRef(kind, section.sectionId(), section.termEnd().toLocalDate());
    }
}
