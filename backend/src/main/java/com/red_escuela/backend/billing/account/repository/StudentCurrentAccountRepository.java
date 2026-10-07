package com.red_escuela.backend.billing.account.repository;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.shared.domain.Currency;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface StudentCurrentAccountRepository extends Repository<StudentCurrentAccount, Long> {

    StudentCurrentAccount save(StudentCurrentAccount account);

    Optional<StudentCurrentAccount> findById(Long id);

    Optional<StudentCurrentAccount> findByStudentIdAndCurrency(Long studentId, Currency currency);

    @Query("select a.id from StudentCurrentAccount a where a.studentId = :studentId and a.currency = :currency")
    Optional<Long> findIdByStudentIdAndCurrency(@Param("studentId") Long studentId, @Param("currency") Currency currency);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StudentCurrentAccount a where a.id = :id")
    Optional<StudentCurrentAccount> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query(value = """
            insert into student_current_account (student_id, currency, balance_amount, balance_amount_currency, balance_side,
                unallocated_credit_amount, unallocated_credit_amount_currency, last_entry_sequence, opened_at, version)
            values (:studentId, :currency, 0, :currency, 'ZERO', 0, :currency, 0, :openedAt, 0)
            on conflict (student_id, currency) do nothing
            """, nativeQuery = true)
    int insertStudentCurrentAccountIfAbsent(@Param("studentId") Long studentId, @Param("currency") String currency, @Param("openedAt") LocalDateTime openedAt);
}
