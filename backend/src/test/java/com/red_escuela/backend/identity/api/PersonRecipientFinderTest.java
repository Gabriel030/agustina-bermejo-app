package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.PersonRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonRecipientFinderTest {

    private final PersonRepository personRepository = mock(PersonRepository.class);
    private final PersonRecipientFinder finder = new PersonRecipientFinder(personRepository);

    @Test
    void findsPeopleHoldingTheRequestedRole() {
        Person director = new Person();
        when(personRepository.findByRolesName("DIRECTOR")).thenReturn(List.of(director));
        when(personRepository.findByRolesName("TEACHER")).thenReturn(List.of());

        assertThat(finder.findByRoleName("DIRECTOR")).containsExactly(director);
        assertThat(finder.findByRoleName("TEACHER")).isEmpty();
        verify(personRepository, never()).findByRolesName("STUDENT");
    }
}
