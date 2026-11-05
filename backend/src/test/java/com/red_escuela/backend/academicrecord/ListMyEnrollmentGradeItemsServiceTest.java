package com.red_escuela.backend.academicrecord;

import com.red_escuela.backend.academicrecord.dto.StudentGradeItemResponse;
import com.red_escuela.backend.academicrecord.service.AcademicRecordEnrollmentOwnership;
import com.red_escuela.backend.academicrecord.service.AcademicRecordGradeItemFinder;
import com.red_escuela.backend.academicrecord.usecase.studentrecord.ListMyEnrollmentGradeItemsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ListMyEnrollmentGradeItemsServiceTest {

    @Mock
    private AcademicRecordEnrollmentOwnership enrollmentOwnership;

    @Mock
    private AcademicRecordGradeItemFinder gradeItemFinder;

    @InjectMocks
    private ListMyEnrollmentGradeItemsService service;

    @Test
    void checksOwnershipBeforeListingTheGradeItems() {
        List<StudentGradeItemResponse> items = List.of(new StudentGradeItemResponse(1L, 4L, "EXAM", 1, null, null, null));
        when(gradeItemFinder.listProgramEnrollmentGradeItems(4L)).thenReturn(items);

        assertThat(service.list(7L, 4L)).isEqualTo(items);

        InOrder order = inOrder(enrollmentOwnership, gradeItemFinder);
        order.verify(enrollmentOwnership).requireProgramEnrollmentOf(4L, 7L);
        order.verify(gradeItemFinder).listProgramEnrollmentGradeItems(4L);
    }

    @Test
    void listsNothingWhenTheStudentDoesNotOwnTheEnrollment() {
        doThrow(new IllegalStateException("not owner")).when(enrollmentOwnership).requireProgramEnrollmentOf(4L, 7L);

        assertThatThrownBy(() -> service.list(7L, 4L)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(gradeItemFinder);
    }
}
