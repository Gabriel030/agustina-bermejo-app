package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.exception.ApplicationDocumentsLockedException;
import org.springframework.stereotype.Service;

@Service
public class ProgramApplicationDocumentEditableGuard {

    public void requireEditable(ProgramEnrollmentApplication application) {
        if (application.getStatus() != ProgramEnrollmentApplicationStatus.PENDING) {
            throw new ApplicationDocumentsLockedException(application.getId(), application.getStatus().name());
        }
    }
}
