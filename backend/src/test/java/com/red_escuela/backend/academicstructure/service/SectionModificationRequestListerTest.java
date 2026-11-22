package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.ProposedSlot;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.SectionModificationProposal;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.repository.SectionModificationProposalRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectionModificationRequestListerTest {

    @Mock
    private SectionModificationProposalRepository proposalRepository;
    @Mock
    private SectionScheduleSlotRepository slotRepository;

    private SectionModificationRequestLister lister;

    @BeforeEach
    void createLister() {
        lister = new SectionModificationRequestLister(proposalRepository, slotRepository);
    }

    @Test
    void noPendingProposalsReturnsEmptyListWithoutLoadingSlots() {
        when(proposalRepository.findPendingOfApprovedCyclesOrderByRequestedAt()).thenReturn(List.of());

        assertThat(lister.listPendingRequests()).isEmpty();
        verifyNoInteractions(slotRepository);
    }

    @Test
    void proposalWithoutRequesterShowsDashAndKeepsCurrentAndProposedSlots() {
        var classroom = classroom(7L, "Aula 7");
        var section = section(10L, "Comisión A", "Álgebra", 3L, "Tecnicatura");
        var proposal = SectionModificationProposal.pending(
                section, true, List.of(new ProposedSlot(classroom, DayOfWeek.FRIDAY, LocalTime.of(9, 0), LocalTime.of(11, 0))),
                null, LocalDateTime.of(2026, 11, 2, 8, 30));
        when(proposalRepository.findPendingOfApprovedCyclesOrderByRequestedAt()).thenReturn(List.of(proposal));
        when(slotRepository.findWithClassroomBySubjectSectionIdIn(List.of(10L))).thenReturn(List.of(
                currentSlot(section, classroom, DayOfWeek.WEDNESDAY, 18),
                currentSlot(section, classroom, DayOfWeek.MONDAY, 18)));

        var response = lister.listPendingRequests().get(0);

        assertThat(response.requestedByName()).isEqualTo("—");
        assertThat(response.sectionId()).isEqualTo(10L);
        assertThat(response.sectionName()).isEqualTo("Comisión A");
        assertThat(response.subjectName()).isEqualTo("Álgebra");
        assertThat(response.offeringName()).isEqualTo("Tecnicatura");
        assertThat(response.cycleId()).isEqualTo(3L);
        assertThat(response.requestedAt()).isEqualTo("2026-11-02T08:30");
        assertThat(response.replacesSchedule()).isTrue();
        assertThat(response.currentSlots()).extracting(slot -> slot.dayOfWeek()).containsExactly("MONDAY", "WEDNESDAY");
        assertThat(response.proposedSlots()).singleElement().satisfies(slot -> {
            assertThat(slot.dayOfWeek()).isEqualTo("FRIDAY");
            assertThat(slot.startTime()).isEqualTo("09:00");
            assertThat(slot.classroomName()).isEqualTo("Aula 7");
        });
    }

    @Test
    void proposalWithRequesterShowsFullNameAndKeepsRepositoryOrder() {
        var requester = new Person();
        requester.setName("Ana");
        requester.setLastname("Pérez");
        var first = SectionModificationProposal.pending(
                section(1L, "A", "Álgebra", 3L, "Tecnicatura"), false, List.of(), requester, LocalDateTime.of(2026, 11, 1, 8, 0));
        var second = SectionModificationProposal.pending(
                section(2L, "B", "Física", 3L, "Tecnicatura"), false, List.of(), null, LocalDateTime.of(2026, 11, 2, 8, 0));
        when(proposalRepository.findPendingOfApprovedCyclesOrderByRequestedAt()).thenReturn(List.of(first, second));
        when(slotRepository.findWithClassroomBySubjectSectionIdIn(any())).thenReturn(List.of());

        var responses = lister.listPendingRequests();

        assertThat(responses).extracting(r -> r.sectionId()).containsExactly(1L, 2L);
        assertThat(responses.get(0).requestedByName()).isEqualTo("Ana Pérez");
        assertThat(responses.get(0).replacesSchedule()).isFalse();
        assertThat(responses.get(0).currentSlots()).isEmpty();
        assertThat(responses.get(0).proposedSlots()).isEmpty();
        assertThat(responses.get(1).requestedByName()).isEqualTo("—");
    }

    private SubjectSection section(Long id, String name, String subjectName, Long cycleId, String programName) {
        var program = new Program();
        program.setName(programName);
        var cycle = new AcademicOfferingCycle();
        cycle.setId(cycleId);
        cycle.setProgram(program);
        var subject = new Subject();
        subject.setName(subjectName);
        var section = new SubjectSection();
        section.setId(id);
        section.setName(name);
        section.setSubject(subject);
        section.setAcademicOfferingCycle(cycle);
        return section;
    }

    private Classroom classroom(Long id, String name) {
        var classroom = new Classroom();
        classroom.setId(id);
        classroom.setName(name);
        return classroom;
    }

    private SectionScheduleSlot currentSlot(SubjectSection section, Classroom classroom, DayOfWeek day, int startHour) {
        var slot = new SectionScheduleSlot();
        slot.setSubjectSection(section);
        slot.setClassroom(classroom);
        slot.setDayOfWeek(day);
        slot.setStartTime(LocalTime.of(startHour, 0));
        slot.setEndTime(LocalTime.of(startHour + 2, 0));
        return slot;
    }
}
