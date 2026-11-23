package com.red_escuela.backend.attendance.specialist;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class CancelProgramSectionClassSessionTransaction {

    private final ClassSessionCanceller classSessionCanceller;

    public CancelProgramSectionClassSessionTransaction(ClassSessionCanceller classSessionCanceller) {
        this.classSessionCanceller = classSessionCanceller;
    }

    @Transactional
    public void cancel(Long sectionId, Long instructorId, LocalDate classDate) {
        classSessionCanceller.cancelProgramSectionSession(sectionId, instructorId, classDate);
    }
}
