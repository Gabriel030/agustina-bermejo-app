package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.exception.InstructorNotFoundException;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentInstructorIdFinderTest {

    @Mock
    private InstructorRepository instructorRepository;

    @BeforeEach
    void authenticateAsPerson9() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("9", null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsTheIdOfTheInstructorOfTheAuthenticatedPerson() {
        Instructor instructor = new Instructor();
        instructor.setId(7L);
        when(instructorRepository.findByPerson_Id(9L)).thenReturn(Optional.of(instructor));

        assertThat(new CurrentInstructorIdFinder(instructorRepository).currentInstructorId()).isEqualTo(7L);
    }

    @Test
    void throwsInstructorNotFoundWhenTheAuthenticatedPersonIsNotAnInstructor() {
        when(instructorRepository.findByPerson_Id(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new CurrentInstructorIdFinder(instructorRepository).currentInstructorId())
                .isInstanceOf(InstructorNotFoundException.class);
    }
}
