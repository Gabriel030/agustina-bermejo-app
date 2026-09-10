package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetStudentRecordService implements GetStudentRecordUseCase {

    private final StudentRepository studentRepository;

    public GetStudentRecordService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Override
    public Student getRecord(Long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
    }
}
