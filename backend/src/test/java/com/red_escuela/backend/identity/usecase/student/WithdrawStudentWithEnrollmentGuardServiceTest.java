package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.domain.StudentWithdrawalGuard;
import com.red_escuela.backend.identity.dto.WithdrawStudentRequest;
import com.red_escuela.backend.identity.entity.WithdrawalReason;
import com.red_escuela.backend.identity.exception.StudentHasActiveEnrollmentsException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@Tag("D-142")
@Tag("D-250")
class WithdrawStudentWithEnrollmentGuardServiceTest {

    private final StudentWithdrawalGuard guard = mock(StudentWithdrawalGuard.class);
    private final WithdrawStudentService delegate = mock(WithdrawStudentService.class);
    private final WithdrawStudentWithEnrollmentGuardService service = new WithdrawStudentWithEnrollmentGuardService(guard, delegate);
    private final WithdrawStudentCommand command = new WithdrawStudentCommand(7L, new WithdrawStudentRequest(WithdrawalReason.ABANDONO, "x"));

    @Test
    void theStudentIsLockedAndCheckedBeforeTheFileIsWithdrawn() {
        service.withdraw(command);

        InOrder order = inOrder(guard, delegate);
        order.verify(guard).lockStudentAndRequireWithdrawable(7L);
        order.verify(delegate).withdraw(command);
    }

    @Test
    void aStudentWithActiveEnrollmentsIsNeverWithdrawn() {
        doThrow(new StudentHasActiveEnrollmentsException(List.of())).when(guard).lockStudentAndRequireWithdrawable(7L);

        assertThatThrownBy(() -> service.withdraw(command)).isInstanceOf(StudentHasActiveEnrollmentsException.class);

        verifyNoInteractions(delegate);
    }
}
