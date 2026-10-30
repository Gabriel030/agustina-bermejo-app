package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionInstructorRepository;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositorySectionInstructorQueriesTest {

    @Mock
    private SubjectSectionInstructorRepository programInstructorRepository;
    @InjectMocks
    private RepositorySectionInstructorQueries queries;

    @Test
    void batchGroupsBySectionId() {
        var lead = link(1L, 10L, 100L, "Ana", "Paz", InstructorRole.LEAD);
        var assistant = link(1L, 11L, 101L, "Luis", "Gil", InstructorRole.ASSISTANT);
        var other = link(2L, 12L, 102L, "Eva", "Roa", InstructorRole.LEAD);
        when(programInstructorRepository.findBySectionIdIn(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(lead, assistant, other));

        var grouped = queries.findProgramSectionInstructors(List.of(1L, 2L, 3L));

        assertThat(grouped.get(1L)).extracting("instructorId").containsExactly(10L, 11L);
        assertThat(grouped.get(1L).get(0).fullName()).isEqualTo("Ana Paz");
        assertThat(grouped.get(1L).get(0).personId()).isEqualTo(100L);
        assertThat(grouped.get(2L)).extracting("role").containsExactly(InstructorRole.LEAD);
        assertThat(grouped.get(3L)).isEmpty();
    }

    @Test
    void batchWithoutIdsDoesNotQuery() {
        assertThat(queries.findProgramSectionInstructors(List.<Long>of())).isEmpty();
        verifyNoInteractions(programInstructorRepository);
    }

    @Test
    void leadPersonIdReturnsTheLeadPerson() {
        var assistant = link(1L, 11L, 101L, "Luis", "Gil", InstructorRole.ASSISTANT);
        var lead = link(1L, 10L, 100L, "Ana", "Paz", InstructorRole.LEAD);
        when(programInstructorRepository.findBySectionId(1L)).thenReturn(List.of(assistant, lead));

        assertThat(queries.findProgramSectionLeadPersonId(1L)).contains(100L);
    }

    @Test
    void leadPersonIdEmptyWithoutLead() {
        var assistant = link(1L, 11L, 101L, "Luis", "Gil", InstructorRole.ASSISTANT);
        when(programInstructorRepository.findBySectionId(1L)).thenReturn(List.of(assistant));

        assertThat(queries.findProgramSectionLeadPersonId(1L)).isEmpty();
    }

    private SubjectSectionInstructor link(Long sectionId, Long instructorId, Long personId, String name,
                                          String lastname, InstructorRole role) {
        var person = new Person();
        person.setId(personId);
        person.setName(name);
        person.setLastname(lastname);
        var instructor = new Instructor();
        instructor.setId(instructorId);
        instructor.setPerson(person);
        var section = new SubjectSection();
        section.setId(sectionId);
        var link = new SubjectSectionInstructor();
        link.setSection(section);
        link.setInstructor(instructor);
        link.setRole(role);
        return link;
    }
}
