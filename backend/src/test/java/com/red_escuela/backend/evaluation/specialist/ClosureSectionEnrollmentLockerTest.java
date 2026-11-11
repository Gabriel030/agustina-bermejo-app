package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClosureSectionEnrollmentLockerTest {

    private final SectionEnrollmentFinder finder = mock(SectionEnrollmentFinder.class);
    private final ClosureSectionEnrollmentLocker locker = new ClosureSectionEnrollmentLocker(finder);

    @Test
    void locksProgramEnrollmentsInAscendingIdOrderWhateverOrderTheyAreLoaded() {
        when(finder.findMembersOfProgramSection(5L)).thenReturn(List.of(enrollment(30L), enrollment(10L), enrollment(20L)));

        locker.lockMembersInAscendingOrder(SectionKind.PROGRAM, 5L);

        InOrder order = inOrder(finder);
        order.verify(finder).lockProgramEnrollment(10L);
        order.verify(finder).lockProgramEnrollment(20L);
        order.verify(finder).lockProgramEnrollment(30L);
    }

    private SubjectSectionEnrollment enrollment(Long id) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setId(id);
        return enrollment;
    }
}
