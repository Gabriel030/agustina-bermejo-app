package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.ExceptionType;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;
import org.junit.jupiter.api.Test;
import java.util.List;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LateExamApprovalCheckerTest {

    @Mock
    private DirectorApprovalRequestRepository directorApprovalRequestRepository;
    private LateExamApprovalChecker checker;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        checker = new LateExamApprovalChecker(directorApprovalRequestRepository,
                java.time.Clock.fixed(java.time.Instant.parse("2029-05-01T10:00:00Z"), java.time.ZoneId.of("UTC")));
    }

    @Test
    void hasApprovedLateExamIsTrueWhenRepositoryHasApprovedLateExamRequest() {
        when(directorApprovalRequestRepository.hasUnconsumedApproved(1L, 2L, ExceptionType.LATE_EXAM)).thenReturn(true);

        assertThat(checker.hasApprovedLateExam(1L, 2L)).isTrue();
    }

    @Test
    void hasApprovedLateExamIsFalseWhenRepositoryHasNoApprovedRequest() {
        when(directorApprovalRequestRepository.hasUnconsumedApproved(1L, 2L, ExceptionType.LATE_EXAM)).thenReturn(false);

        assertThat(checker.hasApprovedLateExam(1L, 2L)).isFalse();
    }

    @Test
    void consumeMarksTheApprovedRequestAsConsumedAndReturnsTrue() {
        DirectorApprovalRequest request = new DirectorApprovalRequest();
        when(directorApprovalRequestRepository.findByProgramEnrollmentIdAndSubjectIdAndTypeAndStatusAndConsumedAtIsNull(
                1L, 2L, ExceptionType.LATE_EXAM, DirectorApprovalRequestStatus.APPROVED)).thenReturn(List.of(request));

        assertThat(checker.consumeApprovedLateExam(1L, 2L)).isTrue();
        assertThat(request.getConsumedAt()).isNotNull();
        verify(directorApprovalRequestRepository).save(request);
    }

    @Test
    void consumeReturnsFalseWhenThereIsNoUnconsumedApproval() {
        when(directorApprovalRequestRepository.findByProgramEnrollmentIdAndSubjectIdAndTypeAndStatusAndConsumedAtIsNull(
                1L, 2L, ExceptionType.LATE_EXAM, DirectorApprovalRequestStatus.APPROVED)).thenReturn(List.of());

        assertThat(checker.consumeApprovedLateExam(1L, 2L)).isFalse();
        verify(directorApprovalRequestRepository, never()).save(any());
    }
}
