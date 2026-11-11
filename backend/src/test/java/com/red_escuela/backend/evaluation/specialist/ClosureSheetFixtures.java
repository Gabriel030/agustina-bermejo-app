package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.evaluation.api.PaymentsStanding;
import com.red_escuela.backend.evaluation.domain.ClosureSuggestedResult;
import com.red_escuela.backend.evaluation.dto.ClosureSheetAdministrativeResponse;
import com.red_escuela.backend.evaluation.dto.ClosureSheetAvailabilityResponse;
import com.red_escuela.backend.evaluation.dto.ClosureSheetColumnResponse;
import com.red_escuela.backend.evaluation.dto.ClosureSheetGradeResponse;
import com.red_escuela.backend.evaluation.dto.ClosureSheetRequirementsResponse;
import com.red_escuela.backend.evaluation.dto.ClosureSheetResponse;
import com.red_escuela.backend.evaluation.dto.ClosureSheetSchemeResponse;
import com.red_escuela.backend.evaluation.dto.ClosureSheetStudentResponse;
import com.red_escuela.backend.evaluation.dto.ClosureSheetSummaryResponse;
import com.red_escuela.backend.evaluation.dto.SectionInstructorSummary;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.Percentage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

final class ClosureSheetFixtures {

    private ClosureSheetFixtures() {
    }

    static ClosureSheetResponse sheetOf(ClosureStatus status, boolean staff) {
        List<ClosureSheetColumnResponse> columns = List.of(
                new ClosureSheetColumnResponse(GradeItemType.EXAM, 1, true), new ClosureSheetColumnResponse(GradeItemType.EXAM, 2, true));
        List<ClosureSheetStudentResponse> students = List.of(
                student("Muñoz Peña", "Ana", ClosureSuggestedResult.PROMOTES, EnrollmentStatus.REGULAR, staff),
                student("Núñez", "Íñigo", ClosureSuggestedResult.GOES_TO_FINAL, EnrollmentStatus.REGULAR, staff),
                student("Ávila", "Bruno", ClosureSuggestedResult.PENDING, EnrollmentStatus.WITHDRAWN, staff));
        boolean closed = status == ClosureStatus.CLOSED;
        return new ClosureSheetResponse(5L, 9L, "Comisión Única", "Teorías Socioculturales", "Tecnicatura en Educación Inicial",
                "Cuatrimestre 2026", LocalDate.of(2026, 12, 10), List.of(new SectionInstructorSummary("Zoé Gómez", "LEAD")), status,
                new ClosureSheetAvailabilityResponse(false, false, null),
                new ClosureSheetSchemeResponse(null, null, null, 2, 0, 1, null), columns, students,
                new ClosureSheetSummaryResponse(1, 1, 0, 0, 0, 0), closed ? "ACTA-2026-0042" : null,
                closed ? LocalDateTime.of(2026, 12, 20, 10, 0) : null, 1, closed ? "Libro 3" : null, closed ? "Folio 12" : null);
    }

    private static ClosureSheetStudentResponse student(String lastname, String name, ClosureSuggestedResult result,
            EnrollmentStatus enrollmentStatus, boolean staff) {
        return new ClosureSheetStudentResponse(lastname.hashCode() + 0L, lastname, name, "30123456", enrollmentStatus,
                List.of(new ClosureSheetGradeResponse(GradeItemType.EXAM, 1, grade("4.00"), grade("8.00"), true),
                        new ClosureSheetGradeResponse(GradeItemType.EXAM, 2, grade("9.00"), null, true)),
                1, grade("8.50"), Percentage.ofPoints(new BigDecimal("90")), result,
                new ClosureSheetRequirementsResponse(null, true, true, false),
                new ClosureSheetAdministrativeResponse(PaymentsStanding.OK, true, staff ? List.of("DNI") : null),
                List.of(), false, "Observación: ñandú", false);
    }

    private static Grade grade(String value) {
        return Grade.of(new BigDecimal(value));
    }
}
