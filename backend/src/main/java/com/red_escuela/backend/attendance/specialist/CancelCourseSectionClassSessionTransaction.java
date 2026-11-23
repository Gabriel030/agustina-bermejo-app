package com.red_escuela.backend.attendance.specialist;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class CancelCourseSectionClassSessionTransaction {

    private final ClassSessionCanceller classSessionCanceller;

    public CancelCourseSectionClassSessionTransaction(ClassSessionCanceller classSessionCanceller) {
        this.classSessionCanceller = classSessionCanceller;
    }

    @Transactional
    public void cancel(Long sectionId, Long instructorId, LocalDate classDate) {
        classSessionCanceller.cancelCourseSectionSession(sectionId, instructorId, classDate);
    }
}
