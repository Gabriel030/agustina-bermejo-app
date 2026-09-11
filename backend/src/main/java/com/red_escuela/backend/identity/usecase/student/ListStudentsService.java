package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.repository.StudentSpecifications;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

@UseCase
public class ListStudentsService implements ListStudentsUseCase {

    private final StudentRepository studentRepository;

    public ListStudentsService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Override
    public List<Student> list(ListStudentsCommand command) {
        StudentOnboardingStatus effectiveOnboarding = command.onboardingStatus() == null
                ? StudentOnboardingStatus.CONFIRMADO
                : command.onboardingStatus();

        Specification<Student> spec = Specification
                .where(StudentSpecifications.withPersonStatus(command.status()))
                .and(StudentSpecifications.withOnboardingStatus(effectiveOnboarding))
                .and(StudentSpecifications.withSearch(command.search()));

        return studentRepository.findAll(spec, Sort.by("person.lastname", "person.name"));
    }
}
