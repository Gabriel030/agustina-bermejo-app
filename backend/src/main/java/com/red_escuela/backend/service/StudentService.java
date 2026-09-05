package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.WithdrawStudentRequest;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.exception.StudentNotFoundException;
import com.red_escuela.backend.repository.StudentRepository;
import com.red_escuela.backend.repository.StudentSpecifications;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final AuthService authService;

    public StudentService(StudentRepository studentRepository, AuthService authService) {
        this.studentRepository = studentRepository;
        this.authService = authService;
    }

    /**
     * onboardingStatus por defecto es CONFIRMADO (decisión de la spec): sin filtro
     * explícito, el listado de legajos no mezcla prospectos de preinscripción.
     */
    public List<Student> list(StatusType statusFilter, StudentOnboardingStatus onboardingStatus) {
        StudentOnboardingStatus effectiveOnboarding = onboardingStatus == null
                ? StudentOnboardingStatus.CONFIRMADO
                : onboardingStatus;

        Specification<Student> spec = Specification
                .where(StudentSpecifications.withPersonStatus(statusFilter))
                .and(StudentSpecifications.withOnboardingStatus(effectiveOnboarding));

        return studentRepository.findAll(spec);
    }

    public Student verLegajo(Long id) {
        return findStudentOrThrow(id);
    }

    @Transactional
    public void bajaAlumno(Long id, WithdrawStudentRequest request) {
        Student student = findStudentOrThrow(id);
        student.getPerson().setStatus(StatusType.DELETED);
        student.setWithdrawalReason(request.reason());
        student.setWithdrawalDetail(request.detail());
        studentRepository.save(student);
    }

    public void resetearPassword(Long id) {
        Student student = findStudentOrThrow(id);
        authService.requestPasswordReset(student.getPerson().getEmail());
    }

    private Student findStudentOrThrow(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new StudentNotFoundException(id));
    }
}
