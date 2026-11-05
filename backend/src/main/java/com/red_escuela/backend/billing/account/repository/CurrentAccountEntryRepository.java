package com.red_escuela.backend.billing.account.repository;

import com.red_escuela.backend.billing.account.domain.CurrentAccountEntry;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CurrentAccountEntryRepository extends Repository<CurrentAccountEntry, Long> {

    CurrentAccountEntry save(CurrentAccountEntry entry);

    Optional<CurrentAccountEntry> findById(Long id);

    List<CurrentAccountEntry> findByStudentCurrentAccountIdOrderBySequenceNumberAsc(Long studentCurrentAccountId);

    boolean existsByCompensatesEntryId(Long compensatesEntryId);

    @Query("""
            select e from CurrentAccountEntry e
            where e.paymentId = :paymentId
              and e.entryType = com.red_escuela.backend.billing.account.domain.CurrentAccountEntryType.PAYMENT_RECEIVED
              and not exists (select 1 from CurrentAccountEntry c where c.compensatesEntryId = e.id)
            """)
    Optional<CurrentAccountEntry> findPaymentCreditNotYetReversed(@Param("paymentId") Long paymentId);

    boolean existsByCreditNoteId(Long creditNoteId);
}
