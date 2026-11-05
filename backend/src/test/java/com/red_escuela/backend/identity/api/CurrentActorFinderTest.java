package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.exception.PersonNotFoundException;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentActorFinderTest {

    @Mock
    private PersonRepository personRepository;
    @Mock
    private StudentRepository studentRepository;
    @InjectMocks
    private CurrentActorFinder finder;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String name) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(name, "n/a", AuthorityUtils.NO_AUTHORITIES));
    }

    private Student studentWithId(Long id) {
        Student student = mock(Student.class);
        when(student.getId()).thenReturn(id);
        return student;
    }

    @Test
    void currentPersonIdIsTheAuthenticatedName() {
        authenticateAs("11");

        assertThat(finder.currentPersonId()).isEqualTo(11L);
    }

    @Test
    void currentPersonReturnsTheStoredPerson() {
        authenticateAs("11");
        Person person = mock(Person.class);
        when(personRepository.findById(11L)).thenReturn(Optional.of(person));

        assertThat(finder.currentPerson()).isSameAs(person);
    }

    @Test
    void currentPersonFailsWhenThePersonDoesNotExist() {
        authenticateAs("11");
        when(personRepository.findById(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finder.currentPerson()).isInstanceOf(PersonNotFoundException.class);
    }

    @Test
    void currentStudentIdIsTheIdOfTheStudentOfTheAuthenticatedPerson() {
        authenticateAs("11");
        Student student = studentWithId(5L);
        when(studentRepository.findByPerson_Id(11L)).thenReturn(Optional.of(student));

        assertThat(finder.currentStudentId()).isEqualTo(5L);
    }

    @Test
    void currentStudentIdFailsWhenThePersonIsNotAStudent() {
        authenticateAs("11");
        when(studentRepository.findByPerson_Id(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finder.currentStudentId()).isInstanceOf(StudentNotFoundException.class);
    }

    @Test
    void findCurrentStudentIdReturnsTheStudentId() {
        authenticateAs("11");
        Student student = studentWithId(5L);
        when(studentRepository.findByPerson_Id(11L)).thenReturn(Optional.of(student));

        assertThat(finder.findCurrentStudentId()).contains(5L);
    }

    @Test
    void findCurrentStudentIdIsEmptyWhenThePersonIsNotAStudent() {
        authenticateAs("11");
        when(studentRepository.findByPerson_Id(11L)).thenReturn(Optional.empty());

        assertThat(finder.findCurrentStudentId()).isEmpty();
    }

    @Test
    void findCurrentStudentIdIsEmptyWithoutAuthentication() {
        assertThat(finder.findCurrentStudentId()).isEmpty();
    }

    @Test
    void findCurrentStudentIdIsEmptyForAnonymousAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(finder.findCurrentStudentId()).isEmpty();
    }
}
