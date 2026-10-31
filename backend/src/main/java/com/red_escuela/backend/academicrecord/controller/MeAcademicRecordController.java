package com.red_escuela.backend.academicrecord.controller;

import com.red_escuela.backend.academicrecord.dto.StudentAttendanceResponse;
import com.red_escuela.backend.academicrecord.dto.StudentAttendanceRiskResponse;
import com.red_escuela.backend.evaluation.dto.GradeItemResponse;
import com.red_escuela.backend.evaluation.service.GradeItemService;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.academicrecord.usecase.studentrecord.ListMyEnrollmentAttendanceUseCase;
import com.red_escuela.backend.academicrecord.usecase.studentrecord.ReadMyEnrollmentAttendanceRiskUseCase;
import com.red_escuela.backend.academicrecord.usecase.studentrecord.SubmitMyAbsenceProofUseCase;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/me")
public class MeAcademicRecordController {
    private final CurrentStudentService currentStudentService;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;
    private final GradeItemService gradeItemService;
    private final ListMyEnrollmentAttendanceUseCase listMyEnrollmentAttendanceUseCase;
    private final ReadMyEnrollmentAttendanceRiskUseCase readMyEnrollmentAttendanceRiskUseCase;
    private final SubmitMyAbsenceProofUseCase submitMyAbsenceProofUseCase;

    public MeAcademicRecordController(
            CurrentStudentService currentStudentService,
            SectionEnrollmentFinder sectionEnrollmentFinder,
            GradeItemService gradeItemService,
            ListMyEnrollmentAttendanceUseCase listMyEnrollmentAttendanceUseCase,
            ReadMyEnrollmentAttendanceRiskUseCase readMyEnrollmentAttendanceRiskUseCase,
            SubmitMyAbsenceProofUseCase submitMyAbsenceProofUseCase) {
        this.currentStudentService = currentStudentService;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
        this.gradeItemService = gradeItemService;
        this.listMyEnrollmentAttendanceUseCase = listMyEnrollmentAttendanceUseCase;
        this.readMyEnrollmentAttendanceRiskUseCase = readMyEnrollmentAttendanceRiskUseCase;
        this.submitMyAbsenceProofUseCase = submitMyAbsenceProofUseCase;
    }

    @GetMapping("/section-enrollments/{id}/attendance")
    public List<StudentAttendanceResponse> myAttendance(@PathVariable Long id) {
        return listMyEnrollmentAttendanceUseCase.list(currentStudentService.resolveCurrentStudent().getId(), id);
    }

    @GetMapping("/section-enrollments/{id}/attendance/risk")
    public StudentAttendanceRiskResponse myAttendanceRisk(@PathVariable Long id) {
        return readMyEnrollmentAttendanceRiskUseCase.read(currentStudentService.resolveCurrentStudent().getId(), id);
    }

    @GetMapping("/section-enrollments/{id}/grade-items")
    public List<GradeItemResponse> myGrades(@PathVariable Long id) {
        sectionEnrollmentFinder.requireProgramEnrollmentOf(id, currentStudentService.resolveCurrentStudent().getId());
        return gradeItemService.listByProgramEnrollment(id).stream().map(GradeItemResponse::from).toList();
    }

    @PostMapping(value = "/attendance/{id}/justify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StudentAttendanceResponse justifyMyAbsence(
            @PathVariable Long id, @RequestParam(required = false) String reason, @RequestPart MultipartFile comprobante) {
        return submitMyAbsenceProofUseCase.submit(
                currentStudentService.resolveCurrentStudent().getId(), id, reason, comprobante);
    }
}
