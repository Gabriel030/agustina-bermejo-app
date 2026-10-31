package com.red_escuela.backend.academicstructure.usecase.classroom;

import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.exception.ClassroomNotFoundException;
import com.red_escuela.backend.academicstructure.repository.ClassroomRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeactivateClassroomServiceTest {

    @Mock
    private ClassroomRepository classroomRepository;

    @Test
    void deactivatingAnActiveClassroomMarksItInactiveAndPersistsIt() {
        var classroom = new Classroom();
        classroom.setStatus(StatusType.ACTIVE);
        when(classroomRepository.findById(5L)).thenReturn(Optional.of(classroom));

        new DeactivateClassroomService(classroomRepository).deactivate(5L);

        assertThat(classroom.getStatus()).isEqualTo(StatusType.INACTIVE);
        verify(classroomRepository).save(classroom);
    }

    @Test
    void deactivatingAnUnknownClassroomFailsWithClassroomNotFound() {
        when(classroomRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DeactivateClassroomService(classroomRepository).deactivate(5L))
                .isInstanceOf(ClassroomNotFoundException.class);

        verify(classroomRepository, never()).save(any());
    }
}
