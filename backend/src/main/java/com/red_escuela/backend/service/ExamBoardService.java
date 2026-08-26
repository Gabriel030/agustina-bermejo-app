package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateExamBoardRequest;
import com.red_escuela.backend.entity.CourseSubject;
import com.red_escuela.backend.entity.ExamBoard;
import com.red_escuela.backend.entity.ExamBoardInstructor;
import com.red_escuela.backend.entity.ExamBoardRegistration;
import com.red_escuela.backend.entity.ExamBoardStatus;
import com.red_escuela.backend.entity.Instructor;
import com.red_escuela.backend.entity.Subject;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.exception.CourseSubjectNotFoundException;
import com.red_escuela.backend.exception.ExamBoardNotFoundException;
import com.red_escuela.backend.exception.InstructorNotFoundException;
import com.red_escuela.backend.exception.SubjectNotFoundException;
import com.red_escuela.backend.exception.TermNotFoundException;
import com.red_escuela.backend.repository.CourseSubjectRepository;
import com.red_escuela.backend.repository.ExamBoardInstructorRepository;
import com.red_escuela.backend.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.repository.ExamBoardRepository;
import com.red_escuela.backend.repository.InstructorRepository;
import com.red_escuela.backend.repository.SubjectRepository;
import com.red_escuela.backend.repository.TermRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * RF-PR-09/10: mesas de examen organizadas por Subject/CourseSubject + Term (decisión 7 de la
 * spec técnica), no por comisión — un alumno regular en una materia rinde en cualquier mesa de
 * esa materia, sin importar en qué comisión cursó.
 */
@Service
public class ExamBoardService {

    private final ExamBoardRepository examBoardRepository;
    private final ExamBoardInstructorRepository examBoardInstructorRepository;
    private final ExamBoardRegistrationRepository examBoardRegistrationRepository;
    private final SubjectRepository subjectRepository;
    private final CourseSubjectRepository courseSubjectRepository;
    private final TermRepository termRepository;
    private final InstructorRepository instructorRepository;

    public ExamBoardService(
            ExamBoardRepository examBoardRepository,
            ExamBoardInstructorRepository examBoardInstructorRepository,
            ExamBoardRegistrationRepository examBoardRegistrationRepository,
            SubjectRepository subjectRepository,
            CourseSubjectRepository courseSubjectRepository,
            TermRepository termRepository,
            InstructorRepository instructorRepository) {
        this.examBoardRepository = examBoardRepository;
        this.examBoardInstructorRepository = examBoardInstructorRepository;
        this.examBoardRegistrationRepository = examBoardRegistrationRepository;
        this.subjectRepository = subjectRepository;
        this.courseSubjectRepository = courseSubjectRepository;
        this.termRepository = termRepository;
        this.instructorRepository = instructorRepository;
    }

    @Transactional
    public ExamBoard crear(CreateExamBoardRequest request) {
        boolean hasSubject = request.subjectId() != null;
        boolean hasCourseSubject = request.courseSubjectId() != null;
        if (hasSubject == hasCourseSubject) {
            throw new IllegalArgumentException(
                    "La mesa de examen tiene que referenciar exactamente una materia: subjectId O courseSubjectId");
        }
        Term term = termRepository.findById(request.termId())
                .orElseThrow(() -> new TermNotFoundException(request.termId()));

        ExamBoard examBoard = new ExamBoard();
        if (hasSubject) {
            Subject subject = subjectRepository.findById(request.subjectId())
                    .orElseThrow(() -> new SubjectNotFoundException(request.subjectId()));
            examBoard.setSubject(subject);
        } else {
            CourseSubject courseSubject = courseSubjectRepository.findById(request.courseSubjectId())
                    .orElseThrow(() -> new CourseSubjectNotFoundException(request.courseSubjectId()));
            examBoard.setCourseSubject(courseSubject);
        }
        examBoard.setTerm(term);
        examBoard.setExamDate(request.examDate());
        examBoard.setRegistrationStart(request.registrationStart());
        examBoard.setRegistrationEnd(request.registrationEnd());
        examBoard.setStatus(ExamBoardStatus.SCHEDULED);
        return examBoardRepository.save(examBoard);
    }

    @Transactional
    public void asignarTribunal(Long examBoardId, Long instructorId) {
        ExamBoard examBoard = findOrThrow(examBoardId);
        Instructor instructor = instructorRepository.findById(instructorId)
                .orElseThrow(() -> new InstructorNotFoundException(instructorId));
        if (examBoardInstructorRepository.existsByExamBoardIdAndInstructorId(examBoardId, instructorId)) {
            return;
        }
        ExamBoardInstructor asignacion = new ExamBoardInstructor();
        asignacion.setExamBoard(examBoard);
        asignacion.setInstructor(instructor);
        examBoardInstructorRepository.save(asignacion);
    }

    @Transactional
    public ExamBoard cerrarActa(Long examBoardId) {
        ExamBoard examBoard = findOrThrow(examBoardId);
        // Idempotente: cerrar un acta ya cerrada no es un error de negocio, mismo criterio que
        // las operaciones de desactivación de Estructura académica (no-op si ya está en el
        // estado destino).
        examBoard.setStatus(ExamBoardStatus.CLOSED);
        return examBoardRepository.save(examBoard);
    }

    public List<ExamBoardRegistration> listarAnotados(Long examBoardId) {
        findOrThrow(examBoardId);
        return examBoardRegistrationRepository.findByExamBoardId(examBoardId);
    }

    public ExamBoard verDetalle(Long id) {
        return findOrThrow(id);
    }

    private ExamBoard findOrThrow(Long id) {
        return examBoardRepository.findById(id).orElseThrow(() -> new ExamBoardNotFoundException(id));
    }
}
