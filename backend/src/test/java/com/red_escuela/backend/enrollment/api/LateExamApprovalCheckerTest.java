package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.ExceptionType;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LateExamApprovalCheckerTest {

    @Mock
    private DirectorApprovalRequestRepository directorApprovalRequestRepository;
    @InjectMocks
    private LateExamApprovalChecker checker;

    @Test
    void hasApprovedLateExamIsTrueWhenRepositoryHasApprovedLateExamRequest() {
        when(directorApprovalRequestRepository.hasApproved(1L, 2L, ExceptionType.LATE_EXAM)).thenReturn(true);

        assertThat(checker.hasApprovedLateExam(1L, 2L)).isTrue();
    }

    @Test
    void hasApprovedLateExamIsFalseWhenRepositoryHasNoApprovedRequest() {
        when(directorApprovalRequestRepository.hasApproved(1L, 2L, ExceptionType.LATE_EXAM)).thenReturn(false);

        assertThat(checker.hasApprovedLateExam(1L, 2L)).isFalse();
    }
}
