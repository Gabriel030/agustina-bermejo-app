package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.StudentDocumentResponse;
import com.red_escuela.backend.enrollment.repository.StudentDocumentRepository;
import com.red_escuela.backend.identity.api.StudentExistenceLookup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/students")
@Tag(name = "student-controller")
public class StudentDocumentController {
    private final StudentExistenceLookup studentExistenceLookup;
    private final StudentDocumentRepository studentDocumentRepository;

    public StudentDocumentController(StudentExistenceLookup studentExistenceLookup,
                                     StudentDocumentRepository studentDocumentRepository) {
        this.studentExistenceLookup = studentExistenceLookup;
        this.studentDocumentRepository = studentDocumentRepository;
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    @Operation(operationId = "Student_getDocuments")
    public List<StudentDocumentResponse> getDocuments(@PathVariable Long id) {
        studentExistenceLookup.requireExistingStudent(id);
        return studentDocumentRepository.findByStudentId(id).stream()
                .map(StudentDocumentResponse::from).toList();
    }
}
