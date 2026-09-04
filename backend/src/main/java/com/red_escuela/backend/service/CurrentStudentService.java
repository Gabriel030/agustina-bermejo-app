package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.exception.StudentNotFoundException;
import com.red_escuela.backend.repository.StudentRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resuelve el Student del alumno autenticado a partir del "sub" del JWT (personId),
 * nunca de un id de la URL — mismo mecanismo que PersonProfileService.resolveAuthenticatedPerson,
 * usado por todos los endpoints /me/... de esta spec.
 */
@Service
public class CurrentStudentService {

    private final StudentRepository studentRepository;

    public CurrentStudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public Student resolveCurrentStudent() {
        Long personId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        return studentRepository.findByPerson_Id(personId)
                .orElseThrow(() -> new StudentNotFoundException(personId));
    }

    // Variante no-throwing para endpoints compartidos entre staff y autoservicio (ej.
    // PaymentPlanSelectionService, PaymentService): un Administrativo/Director autenticado no
    // tiene fila Student — Optional.empty() en ese caso es la señal de "es staff, no hace falta
    // chequear ownership", en vez de forzar al caller a capturar StudentNotFoundException para
    // lo mismo. También cubre el caso de un caller interno sin ningún contexto de seguridad
    // seteado (ej. PaymentReceiptService.aprobar llamado directo desde un flujo de sistema, sin
    // pasar por un controller) — sin autenticación, tampoco hay ownership que chequear.
    public Optional<Student> findCurrentStudent() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        Long personId = Long.valueOf(authentication.getName());
        return studentRepository.findByPerson_Id(personId);
    }
}
