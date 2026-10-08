package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationResponse;
import com.red_escuela.backend.enrollment.dto.RejectProgramEnrollmentApplicationRequest;
import com.red_escuela.backend.enrollment.dto.StudentDocumentResponse;
import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentApplicationNotFoundException;
import com.red_escuela.backend.enrollment.repository.StudentDocumentRepository;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.AddProgramEnrollmentApplicationDocumentCommand;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.AddProgramEnrollmentApplicationDocumentUseCase;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.ApproveProgramEnrollmentApplicationCommand;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.ApproveProgramEnrollmentApplicationUseCase;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.GetProgramEnrollmentApplicationUseCase;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.ListMyProgramEnrollmentApplicationsUseCase;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.ListPendingProgramEnrollmentApplicationsUseCase;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.RejectProgramEnrollmentApplicationCommand;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.RejectProgramEnrollmentApplicationUseCase;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.RequestProgramEnrollmentApplicationCommand;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.RequestProgramEnrollmentApplicationUseCase;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class ProgramEnrollmentApplicationController {
    private final CurrentStudentService currentStudentService;
    private final CurrentPersonService currentPersonService;
    private final RequestProgramEnrollmentApplicationUseCase requestProgramEnrollmentApplicationUseCase;
    private final ListMyProgramEnrollmentApplicationsUseCase listMyProgramEnrollmentApplicationsUseCase;
    private final ListPendingProgramEnrollmentApplicationsUseCase listPendingProgramEnrollmentApplicationsUseCase;
    private final GetProgramEnrollmentApplicationUseCase getProgramEnrollmentApplicationUseCase;
    private final AddProgramEnrollmentApplicationDocumentUseCase addProgramEnrollmentApplicationDocumentUseCase;
    private final ApproveProgramEnrollmentApplicationUseCase approveProgramEnrollmentApplicationUseCase;
    private final RejectProgramEnrollmentApplicationUseCase rejectProgramEnrollmentApplicationUseCase;
    private final StudentDocumentRepository studentDocumentRepository;

    public ProgramEnrollmentApplicationController(
            CurrentStudentService currentStudentService,
            CurrentPersonService currentPersonService,
            RequestProgramEnrollmentApplicationUseCase requestProgramEnrollmentApplicationUseCase,
            ListMyProgramEnrollmentApplicationsUseCase listMyProgramEnrollmentApplicationsUseCase,
            ListPendingProgramEnrollmentApplicationsUseCase listPendingProgramEnrollmentApplicationsUseCase,
            GetProgramEnrollmentApplicationUseCase getProgramEnrollmentApplicationUseCase,
            AddProgramEnrollmentApplicationDocumentUseCase addProgramEnrollmentApplicationDocumentUseCase,
            ApproveProgramEnrollmentApplicationUseCase approveProgramEnrollmentApplicationUseCase,
            RejectProgramEnrollmentApplicationUseCase rejectProgramEnrollmentApplicationUseCase,
            StudentDocumentRepository studentDocumentRepository) {
        this.currentStudentService = currentStudentService;
        this.currentPersonService = currentPersonService;
        this.requestProgramEnrollmentApplicationUseCase = requestProgramEnrollmentApplicationUseCase;
        this.listMyProgramEnrollmentApplicationsUseCase = listMyProgramEnrollmentApplicationsUseCase;
        this.listPendingProgramEnrollmentApplicationsUseCase = listPendingProgramEnrollmentApplicationsUseCase;
        this.getProgramEnrollmentApplicationUseCase = getProgramEnrollmentApplicationUseCase;
        this.addProgramEnrollmentApplicationDocumentUseCase = addProgramEnrollmentApplicationDocumentUseCase;
        this.approveProgramEnrollmentApplicationUseCase = approveProgramEnrollmentApplicationUseCase;
        this.rejectProgramEnrollmentApplicationUseCase = rejectProgramEnrollmentApplicationUseCase;
        this.studentDocumentRepository = studentDocumentRepository;
    }

    @PostMapping("/me/program-enrollment-applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ProgramEnrollmentApplicationResponse request(
            @RequestParam Long programId, @RequestParam Long cohortId) {
        Student student = currentStudentService.resolveCurrentStudent();
        ProgramEnrollmentApplication application = requestProgramEnrollmentApplicationUseCase.request(
                new RequestProgramEnrollmentApplicationCommand(student, programId, cohortId));
        return ProgramEnrollmentApplicationResponse.from(application);
    }

    @GetMapping("/me/program-enrollment-applications")
    public List<ProgramEnrollmentApplicationResponse> mine() {
        Student student = currentStudentService.resolveCurrentStudent();
        return listMyProgramEnrollmentApplicationsUseCase.list(student.getId()).stream()
                .map(ProgramEnrollmentApplicationResponse::from).toList();
    }

    @PostMapping("/me/program-enrollment-applications/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public void addDocument(@PathVariable Long id, @RequestParam DocumentType type, @RequestPart MultipartFile file) {
        Student student = currentStudentService.resolveCurrentStudent();
        ProgramEnrollmentApplication application = getProgramEnrollmentApplicationUseCase.get(id);
        requireOwner(application, student);
        addProgramEnrollmentApplicationDocumentUseCase.add(new AddProgramEnrollmentApplicationDocumentCommand(id, type, file));
    }

    @GetMapping("/me/documents")
    public List<StudentDocumentResponse> myDocuments() {
        Student student = currentStudentService.resolveCurrentStudent();
        return studentDocumentRepository.findByStudentId(student.getId()).stream()
                .map(StudentDocumentResponse::from).toList();
    }

    @GetMapping("/program-enrollment-applications")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public List<ProgramEnrollmentApplicationResponse> listPending() {
        return listPendingProgramEnrollmentApplicationsUseCase.list().stream()
                .map(ProgramEnrollmentApplicationResponse::from).toList();
    }

    @PostMapping("/program-enrollment-applications/{id}/approve")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ProgramEnrollmentApplicationResponse approve(@PathVariable Long id) {
        Person decidedBy = currentPersonService.resolveCurrentPerson();
        return ProgramEnrollmentApplicationResponse.from(
                approveProgramEnrollmentApplicationUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(id, decidedBy)));
    }

    @PostMapping("/program-enrollment-applications/{id}/reject")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ProgramEnrollmentApplicationResponse reject(
            @PathVariable Long id, @Valid @RequestBody RejectProgramEnrollmentApplicationRequest request) {
        Person decidedBy = currentPersonService.resolveCurrentPerson();
        return ProgramEnrollmentApplicationResponse.from(
                rejectProgramEnrollmentApplicationUseCase.reject(new RejectProgramEnrollmentApplicationCommand(id, decidedBy, request.reason())));
    }

    private void requireOwner(ProgramEnrollmentApplication application, Student student) {
        if (!application.getStudent().getId().equals(student.getId())) {
            throw new ProgramEnrollmentApplicationNotFoundException(application.getId());
        }
    }
}
