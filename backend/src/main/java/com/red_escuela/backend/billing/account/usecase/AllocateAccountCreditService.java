package com.red_escuela.backend.billing.account.usecase;

import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationCommand;
import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationResult;
import com.red_escuela.backend.billing.account.service.AccountCreditAllocationOperation;
import com.red_escuela.backend.billing.account.repository.StudentCurrentAccountRepository;
import com.red_escuela.backend.billing.operation.AuthorizationPendingException;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.account.service.CurrentAccountActorResolver;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@UseCase
public class AllocateAccountCreditService implements AllocateAccountCreditUseCase {
    private final BillingOperationPipeline pipeline;
    private final AccountCreditAllocationOperation operation;
    private final StudentCurrentAccountRepository accountRepository;
    private final CurrentAccountActorResolver actorResolver;
    private final Clock clock;

    public AllocateAccountCreditService(BillingOperationPipeline pipeline, AccountCreditAllocationOperation operation,
                                        StudentCurrentAccountRepository accountRepository, CurrentAccountActorResolver actorResolver, Clock clock) {
        this.pipeline = pipeline;
        this.operation = operation;
        this.accountRepository = accountRepository;
        this.actorResolver = actorResolver;
        this.clock = clock;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<AccountCreditAllocationResult> allocateCreditOfStudentAccounts(Long studentId) {
        StatusChangeActor actor = actorResolver.resolveCurrentPersonAsActor();
        LocalDate businessDate = LocalDate.now(clock);
        return accountRepository.findIdsByStudentId(studentId).stream()
                .map(accountId -> pipeline.runOperation(operation, new AccountCreditAllocationCommand(accountId, businessDate, actor))
                        .resultOrThrowIfAwaitingAuthorization(AuthorizationPendingException::new))
                .toList();
    }
}
