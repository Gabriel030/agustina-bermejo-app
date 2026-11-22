package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddProgramEnrollmentApplicationDocumentTransaction {

    private final ProgramEnrollmentApplicationFinder applicationFinder;
    private final ProgramApplicationDocumentRecorder documentRecorder;
    private final ProgramApplicationDocumentEditableGuard editableGuard;

    public AddProgramEnrollmentApplicationDocumentTransaction(ProgramEnrollmentApplicationFinder applicationFinder,
                                                            ProgramApplicationDocumentRecorder documentRecorder,
                                                            ProgramApplicationDocumentEditableGuard editableGuard) {
        this.applicationFinder = applicationFinder;
        this.documentRecorder = documentRecorder;
        this.editableGuard = editableGuard;
    }

    @Transactional
    public StudentDocument add(Long applicationId, DocumentType type, MultipartFile file) {
        ProgramEnrollmentApplication application = applicationFinder.findOrThrow(applicationId);
        editableGuard.requireEditable(application);
        return documentRecorder.record(application, type, file);
    }
}
