package com.red_escuela.backend.attendance.usecase.attendancechangerequest;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.repository.AttendanceChangeRequestRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ListMyAttendanceChangeRequestsServiceTest {

    private final AttendanceChangeRequestRepository repository = mock(AttendanceChangeRequestRepository.class);
    private final ListMyAttendanceChangeRequestsService service = new ListMyAttendanceChangeRequestsService(repository);

    @Test
    void listsOnlyTheRequestsOfTheGivenInstructorNewestFirst() {
        AttendanceChangeRequest newest = mock(AttendanceChangeRequest.class);
        AttendanceChangeRequest oldest = mock(AttendanceChangeRequest.class);
        when(repository.findByRequestedByIdOrderByCreatedAtDesc(3L)).thenReturn(List.of(newest, oldest));

        assertThat(service.list(3L)).containsExactly(newest, oldest);
        verify(repository).findByRequestedByIdOrderByCreatedAtDesc(3L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void returnsAnEmptyListWhenTheInstructorHasNoRequests() {
        when(repository.findByRequestedByIdOrderByCreatedAtDesc(4L)).thenReturn(List.of());

        assertThat(service.list(4L)).isEmpty();
    }

    @Test
    void doesNotMixTheRequestsOfAnotherInstructor() {
        AttendanceChangeRequest mine = mock(AttendanceChangeRequest.class);
        when(repository.findByRequestedByIdOrderByCreatedAtDesc(3L)).thenReturn(List.of(mine));
        when(repository.findByRequestedByIdOrderByCreatedAtDesc(8L)).thenReturn(List.of());

        assertThat(service.list(8L)).isEmpty();
        assertThat(service.list(3L)).containsExactly(mine);
    }
}
