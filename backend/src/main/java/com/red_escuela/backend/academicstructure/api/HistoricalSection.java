package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.SectionShift;
import com.red_escuela.backend.academicstructure.domain.TermType;

public record HistoricalSection(String code, String subjectCode, int year, TermType termType, String name,
        SectionShift shift, String instructorDni, String programName) {
}
