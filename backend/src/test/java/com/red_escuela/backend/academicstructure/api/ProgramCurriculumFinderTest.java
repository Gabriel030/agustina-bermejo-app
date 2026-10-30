package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.academicstructure.entity.ElectiveGroup;
import com.red_escuela.backend.academicstructure.entity.ProgramElectiveGroup;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectPrerequisite;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.repository.ProgramElectiveGroupRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectPrerequisiteRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramCurriculumFinderTest {

    @Mock
    private ProgramSubjectRepository programSubjectRepository;
    @Mock
    private ProgramSubjectPrerequisiteRepository prerequisiteRepository;
    @Mock
    private ProgramElectiveGroupRepository electiveGroupRepository;
    @InjectMocks
    private ProgramCurriculumFinder finder;

    @Test
    void programSubjectsIncludeElectiveGroupIdAndCurricularPlacement() {
        var mandatory = entry(1L, 10L, "Algebra", 6, null);
        mandatory.setCurricularYear(1);
        mandatory.setCurricularTerm(CurricularTerm.values()[0]);
        var elective = entry(2L, 11L, "Arte", 4, 77L);
        when(programSubjectRepository.findByProgramId(5L)).thenReturn(List.of(mandatory, elective));

        var views = finder.findProgramSubjects(5L);

        assertThat(views).containsExactly(
                new ProgramSubjectView(1L, 10L, "Algebra", 6, 1, CurricularTerm.values()[0], null),
                new ProgramSubjectView(2L, 11L, "Arte", 4, null, null, 77L));
        assertThat(views.get(0).isElective()).isFalse();
        assertThat(views.get(1).isElective()).isTrue();
    }

    @Test
    void programSubjectLookupIsEmptyWhenSubjectIsNotInThePlan() {
        when(programSubjectRepository.findByProgramIdAndSubjectId(5L, 99L)).thenReturn(Optional.empty());

        assertThat(finder.findProgramSubject(5L, 99L)).isEmpty();
        assertThat(finder.subjectBelongsToProgram(5L, 99L)).isFalse();
    }

    @Test
    void subjectBelongsToProgramWhenPlanEntryExists() {
        when(programSubjectRepository.findByProgramIdAndSubjectId(5L, 10L))
                .thenReturn(Optional.of(entry(1L, 10L, "Algebra", 6, null)));

        assertThat(finder.subjectBelongsToProgram(5L, 10L)).isTrue();
        assertThat(finder.findProgramSubject(5L, 10L)).get().extracting(ProgramSubjectView::subjectName)
                .isEqualTo("Algebra");
    }

    @Test
    void prerequisitesMapRequirementTypeAndPrerequisiteSubject() {
        var link = new ProgramSubjectPrerequisite();
        link.setPrerequisiteProgramSubject(entry(3L, 12L, "Analisis", 8, null));
        link.setRequirementType(RequirementType.values()[0]);
        when(prerequisiteRepository.findByProgramSubjectId(1L)).thenReturn(List.of(link));

        assertThat(finder.findPrerequisites(1L))
                .containsExactly(new PrerequisiteView(12L, "Analisis", RequirementType.values()[0]));
    }

    @Test
    void electiveGroupRequirementsExposeBounds() {
        var group = new ElectiveGroup();
        group.setId(77L);
        group.setName("Optativas");
        var link = new ProgramElectiveGroup();
        link.setElectiveGroup(group);
        link.setMinRequired(2);
        link.setMaxAllowed(null);
        when(electiveGroupRepository.findByProgramId(5L)).thenReturn(List.of(link));

        assertThat(finder.findElectiveGroupRequirements(5L))
                .containsExactly(new ElectiveGroupRequirementView(77L, "Optativas", 2, null));
    }

    private ProgramSubject entry(Long id, Long subjectId, String name, int credits, Long electiveGroupId) {
        var subject = new Subject();
        subject.setId(subjectId);
        subject.setName(name);
        var entry = new ProgramSubject();
        entry.setId(id);
        entry.setSubject(subject);
        entry.setCredits(credits);
        if (electiveGroupId != null) {
            var group = new ElectiveGroup();
            group.setId(electiveGroupId);
            entry.setElectiveGroup(group);
        }
        return entry;
    }
}
