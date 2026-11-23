package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.api.SectionEnrollmentRecordsPort;
import com.red_escuela.backend.enrollment.exception.SectionHasRecordsException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OwnSectionChangeRecordsGuard {

    private final List<SectionEnrollmentRecordsPort> recordsPorts;

    public OwnSectionChangeRecordsGuard(List<SectionEnrollmentRecordsPort> recordsPorts) {
        this.recordsPorts = recordsPorts;
    }

    public void requireNoRecords(EnrollmentChange change, Long programSectionEnrollmentId) {
        if (change.madeByStaff()) {
            return;
        }
        if (recordsPorts.stream().anyMatch(port -> port.hasProgramEnrollmentRecords(programSectionEnrollmentId))) {
            throw new SectionHasRecordsException();
        }
    }
}
