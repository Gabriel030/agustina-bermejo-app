package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.ApplicationDocumentFiles;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationResponse;
import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.identity.api.StudentEnrollmentEligibility;
import com.red_escuela.backend.identity.api.StudentFinder;
import com.red_escuela.backend.identity.entity.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.EnumMap;
import java.util.Map;

@Service
public class ProgramApplicationWithDocumentsSubmitter {

    private final StudentFinder studentFinder;
    private final StudentEnrollmentEligibility studentEnrollmentEligibility;
    private final RequiredApplicationDocumentsGuard requiredDocumentsGuard;
    private final ProgramApplicationRegistrar applicationRegistrar;
    private final ProgramApplicationDocumentRecorder documentRecorder;

    public ProgramApplicationWithDocumentsSubmitter(StudentFinder studentFinder,
                                                    StudentEnrollmentEligibility studentEnrollmentEligibility,
                                                    RequiredApplicationDocumentsGuard requiredDocumentsGuard,
                                                    ProgramApplicationRegistrar applicationRegistrar,
                                                    ProgramApplicationDocumentRecorder documentRecorder) {
        this.studentFinder = studentFinder;
        this.studentEnrollmentEligibility = studentEnrollmentEligibility;
        this.requiredDocumentsGuard = requiredDocumentsGuard;
        this.applicationRegistrar = applicationRegistrar;
        this.documentRecorder = documentRecorder;
    }

    @Transactional
    public ProgramEnrollmentApplicationResponse submit(Long studentId, Long programId, Long cohortId, ApplicationDocumentFiles files) {
        Map<DocumentType, MultipartFile> documents = new EnumMap<>(DocumentType.class);
        documents.put(DocumentType.DNI_FRENTE, files.dniFront());
        documents.put(DocumentType.DNI_DORSO, files.dniBack());
        documents.put(DocumentType.ANALITICO_SECUNDARIO, files.academicTranscript());
        requiredDocumentsGuard.requireAllFilesPresent(documents);
        Student student = studentFinder.requireById(studentId);
        studentEnrollmentEligibility.requireConfirmedAndActive(student);
        ProgramEnrollmentApplication application = applicationRegistrar.register(student, programId, cohortId);
        documents.forEach((type, file) -> documentRecorder.record(application, type, file));
        return ProgramEnrollmentApplicationResponse.from(application);
    }
}
