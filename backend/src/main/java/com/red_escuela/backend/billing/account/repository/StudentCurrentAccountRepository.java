package com.red_escuela.backend.billing.account.repository;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.shared.domain.Currency;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface StudentCurrentAccountRepository extends Repository<StudentCurrentAccount, Long> {

    StudentCurrentAccount save(StudentCurrentAccount account);

    Optional<StudentCurrentAccount> findById(Long id);

    Optional<StudentCurrentAccount> findByStudentIdAndCurrency(Long studentId, Currency currency);
}
