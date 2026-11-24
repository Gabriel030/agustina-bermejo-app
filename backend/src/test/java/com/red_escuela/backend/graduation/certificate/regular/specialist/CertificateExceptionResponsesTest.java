package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.enrollment.api.EnrollmentNameFinder;
import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionApproval;
import com.red_escuela.backend.graduation.certificate.regular.entity.OrganismKind;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.entity.ValidityDays;
import com.red_escuela.backend.identity.api.PersonNameFinder;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CertificateExceptionResponsesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 10, 0);
    private static final String REASON = "El alumno presentó un reclamo formal ante Defensa del Consumidor";

    private final PersonNameFinder personNameFinder = mock(PersonNameFinder.class);
    private final EnrollmentNameFinder enrollmentNameFinder = mock(EnrollmentNameFinder.class);
    private final CertificateExceptionResponses responses = new CertificateExceptionResponses(personNameFinder, enrollmentNameFinder);

    private CertificateExceptionApproval pending(RegularityKind kind, Long enrollmentId, Long studentId, Long requesterId) {
        return CertificateExceptionApproval.request(kind, enrollmentId, studentId, OrganismKind.COURT, "Causa 1/2026", REASON, requesterId, NOW);
    }

    @Test
    void namesAreResolvedForStudentProgramRequesterAndDecider() {
        CertificateExceptionApproval approval = pending(RegularityKind.PROGRAM, 17L, 5L, 9L);
        approval.approve(4L, "Reclamo verificado con el expediente", new ValidityDays(7), NOW.plusHours(1));
        when(personNameFinder.findFullNamesByStudentId(anyCollection())).thenReturn(Map.of(5L, "Ana Alumna"));
        when(personNameFinder.findFullNamesByPersonId(anyCollection())).thenReturn(Map.of(9L, "Beto Admin", 4L, "Dora Directora"));
        when(enrollmentNameFinder.findProgramNamesByProgramEnrollmentId(anyCollection())).thenReturn(Map.of(17L, "Tecnicatura"));

        CertificateExceptionResponse response = responses.of(approval);

        assertThat(response.studentName()).isEqualTo("Ana Alumna");
        assertThat(response.programName()).isEqualTo("Tecnicatura");
        assertThat(response.requestedByName()).isEqualTo("Beto Admin");
        assertThat(response.decidedByName()).isEqualTo("Dora Directora");
    }

    @Test
    void pendingExceptionHasNullDeciderName() {
        when(personNameFinder.findFullNamesByPersonId(anyCollection())).thenReturn(Map.of(9L, "Beto Admin"));

        CertificateExceptionResponse response = responses.of(pending(RegularityKind.PROGRAM, 17L, 5L, 9L));

        assertThat(response.decidedById()).isNull();
        assertThat(response.decidedByName()).isNull();
        assertThat(response.requestedByName()).isEqualTo("Beto Admin");
    }

    @Test
    void unknownNamesAreNullInsteadOfFailing() {
        CertificateExceptionResponse response = responses.of(pending(RegularityKind.COURSE, 3L, 5L, 9L));

        assertThat(response.studentName()).isNull();
        assertThat(response.programName()).isNull();
        assertThat(response.requestedByName()).isNull();
    }

    @Test
    void listResolvesEachKindOfNameWithASingleBatchedCall() {
        List<CertificateExceptionApproval> approvals = List.of(pending(RegularityKind.PROGRAM, 17L, 5L, 9L),
                pending(RegularityKind.PROGRAM, 18L, 6L, 9L), pending(RegularityKind.COURSE, 3L, 7L, 9L));
        when(enrollmentNameFinder.findCourseNamesByCourseEnrollmentId(anyCollection())).thenReturn(Map.of(3L, "Curso de verano"));

        List<CertificateExceptionResponse> result = responses.all(approvals);

        assertThat(result).hasSize(3);
        assertThat(result.get(2).programName()).isEqualTo("Curso de verano");
        verify(personNameFinder, times(1)).findFullNamesByStudentId(anyCollection());
        verify(personNameFinder, times(1)).findFullNamesByPersonId(anyCollection());
        verify(enrollmentNameFinder, times(1)).findProgramNamesByProgramEnrollmentId(anyCollection());
        verify(enrollmentNameFinder, times(1)).findCourseNamesByCourseEnrollmentId(anyCollection());
    }
}
