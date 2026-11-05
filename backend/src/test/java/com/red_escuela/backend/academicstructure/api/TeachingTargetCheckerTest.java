package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeachingTargetCheckerTest {

    @Mock
    private ProgramRepository programRepository;
    @Mock
    private CourseRepository courseRepository;
    @InjectMocks
    private TeachingTargetChecker checker;

    @Test
    void programExistenceComesFromTheProgramRepositoryOnly() {
        when(programRepository.existsById(4L)).thenReturn(true);
        when(programRepository.existsById(5L)).thenReturn(false);

        assertThat(checker.programExists(4L)).isTrue();
        assertThat(checker.programExists(5L)).isFalse();
    }

    @Test
    void courseExistenceComesFromTheCourseRepositoryOnly() {
        when(courseRepository.existsById(9L)).thenReturn(true);
        when(courseRepository.existsById(8L)).thenReturn(false);

        assertThat(checker.courseExists(9L)).isTrue();
        assertThat(checker.courseExists(8L)).isFalse();
    }
}
