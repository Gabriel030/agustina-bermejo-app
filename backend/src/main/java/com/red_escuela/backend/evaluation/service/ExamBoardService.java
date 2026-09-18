package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.evaluation.dto.CreateExamBoardRequest;
import com.red_escuela.backend.evaluation.dto.ExamBoardRegistrationSummaryResponse;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardInstructor;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.CourseSubjectNotFoundException;
import com.red_escuela.backend.evaluation.exception.ExamBoardNotFoundException;
import com.red_escuela.backend.academicstructure.exception.InstructorNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SubjectNotFoundException;
import com.red_escuela.backend.academicstructure.exception.TermNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardInstructorRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    public ExamBoard create(CreateExamBoardRequest request) {
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
        examBoard.setClassroom(request.classroom());
        examBoard.setStatus(ExamBoardStatus.SCHEDULED);
        return examBoardRepository.save(examBoard);
    }

    public List<ExamBoard> listAvailableBySubject(Long subjectId) {
        return examBoardRepository.findBySubjectIdAndStatus(subjectId, ExamBoardStatus.SCHEDULED);
    }

    @Transactional
    public void assignTribunal(Long examBoardId, Long instructorId) {
        ExamBoard examBoard = findOrThrow(examBoardId);
        Instructor instructor = instructorRepository.findById(instructorId)
                .orElseThrow(() -> new InstructorNotFoundException(instructorId));
        if (examBoardInstructorRepository.existsByExamBoardIdAndInstructorId(examBoardId, instructorId)) {
            return;
        }
        ExamBoardInstructor assignment = new ExamBoardInstructor();
        assignment.setExamBoard(examBoard);
        assignment.setInstructor(instructor);
        examBoardInstructorRepository.save(assignment);
    }

    @Transactional
    public ExamBoard close(Long examBoardId) {
        ExamBoard examBoard = findOrThrow(examBoardId);
        examBoard.setStatus(ExamBoardStatus.CLOSED);
        return examBoardRepository.save(examBoard);
    }

    public List<ExamBoardRegistration> listRegistrants(Long examBoardId) {
        findOrThrow(examBoardId);
        return examBoardRegistrationRepository.findByExamBoardId(examBoardId);
    }

    public List<ExamBoardRegistrationSummaryResponse> listRegistrantsWithDetail(Long examBoardId) {
        return listRegistrants(examBoardId).stream().map(this::toSummary).toList();
    }

    public ExamBoard get(Long id) {
        return findOrThrow(id);
    }

    public List<ExamBoard> list(ExamBoardStatus status) {
        return status == null ? examBoardRepository.findAllByOrderByExamDateDesc()
                : examBoardRepository.findByStatusOrderByExamDateDesc(status);
    }

    private ExamBoardRegistrationSummaryResponse toSummary(ExamBoardRegistration registration) {
        String carrera;
        Integer attemptNumber;
        Person person;
        if (registration.getProgramSubjectSectionEnrollment() != null) {
            var enrollment = registration.getProgramSubjectSectionEnrollment();
            carrera = enrollment.getProgramEnrollment().getProgram().getName();
            attemptNumber = enrollment.getAttemptNumber();
            person = enrollment.getStudent().getPerson();
        } else {
            var enrollment = registration.getCourseSubjectSectionEnrollment();
            carrera = "Curso suelto";
            attemptNumber = enrollment.getAttemptNumber();
            person = enrollment.getStudent().getPerson();
        }
        return new ExamBoardRegistrationSummaryResponse(
                registration.getId(), person.getName() + " " + person.getLastname(), person.getDni(),
                carrera, attemptNumber, registration.getStatus().name());
    }

    private ExamBoard findOrThrow(Long id) {
        return examBoardRepository.findById(id).orElseThrow(() -> new ExamBoardNotFoundException(id));
    }
}
