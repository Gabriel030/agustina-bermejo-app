package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.exception.InstructorNotFoundException;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositoryInstructorQueriesTest {

    @Mock
    private InstructorRepository instructorRepository;
    @InjectMocks
    private RepositoryInstructorQueries queries;

    @Test
    void requireExistsThrowsWhenMissing() {
        when(instructorRepository.existsById(4L)).thenReturn(false);

        assertThatThrownBy(() -> queries.requireExists(4L)).isInstanceOf(InstructorNotFoundException.class);
    }

    @Test
    void requireExistsPassesWhenPresent() {
        when(instructorRepository.existsById(4L)).thenReturn(true);

        queries.requireExists(4L);
    }
}
