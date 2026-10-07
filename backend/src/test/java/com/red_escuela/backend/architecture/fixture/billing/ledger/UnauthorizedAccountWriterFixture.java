package com.red_escuela.backend.architecture.fixture.billing.ledger;

import com.red_escuela.backend.billing.account.domain.CurrentAccountEntry;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.repository.CurrentAccountEntryRepository;
import com.red_escuela.backend.billing.account.repository.StudentCurrentAccountRepository;

public class UnauthorizedAccountWriterFixture {

    private StudentCurrentAccountRepository accountRepository;
    private CurrentAccountEntryRepository entryRepository;

    public void write(StudentCurrentAccount account, CurrentAccountEntry entry) {
        accountRepository.save(account);
        entryRepository.save(entry);
    }
}
