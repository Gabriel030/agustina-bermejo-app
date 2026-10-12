package com.red_escuela.backend.academicstructure.usecase.classroom;

import com.red_escuela.backend.academicstructure.dto.ClassroomResponse;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.repository.ClassroomRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
@Transactional(readOnly = true)
public class ListClassroomsService implements ListClassroomsUseCase {

    private final ClassroomRepository classroomRepository;

    public ListClassroomsService(ClassroomRepository classroomRepository) {
        this.classroomRepository = classroomRepository;
    }

    @Override
    public List<ClassroomResponse> list() {
        return classroomRepository.findByStatus(StatusType.ACTIVE)
                .stream()
                .map(ClassroomResponse::from)
                .toList();
    }
}
