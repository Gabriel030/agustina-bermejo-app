package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.StudentDocumentResponse;
import com.red_escuela.backend.enrollment.repository.StudentDocumentRepository;
import com.red_escuela.backend.identity.api.StudentExistenceChecker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/students")
@Tag(name = "student-controller")
public class StudentDocumentController {
    private final StudentExistenceChecker studentExistenceChecker;
    private final StudentDocumentRepository studentDocumentRepository;

    public StudentDocumentController(StudentExistenceChecker studentExistenceChecker,
                                     StudentDocumentRepository studentDocumentRepository) {
        this.studentExistenceChecker = studentExistenceChecker;
        this.studentDocumentRepository = studentDocumentRepository;
    }

    @GetMapping("/{id}/documents")
    @RequiresPermission(code = "enrollment.read_prospects", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    @Operation(operationId = "Student_getDocuments")
    public List<StudentDocumentResponse> getDocuments(@PathVariable Long id) {
        studentExistenceChecker.requireExistingStudent(id);
        return studentDocumentRepository.findByStudentId(id).stream()
                .map(StudentDocumentResponse::from).toList();
    }
}
