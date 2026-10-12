package com.red_escuela.backend.billing.account.repository;

import com.red_escuela.backend.billing.account.domain.CurrentAccountEntry;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

public interface CurrentAccountEntryRepository extends Repository<CurrentAccountEntry, Long> {

    CurrentAccountEntry save(CurrentAccountEntry entry);

    Optional<CurrentAccountEntry> findById(Long id);

    List<CurrentAccountEntry> findByStudentCurrentAccountIdOrderBySequenceNumberAsc(Long studentCurrentAccountId);

    boolean existsByCompensatesEntryId(Long compensatesEntryId);

    boolean existsByCreditNoteId(Long creditNoteId);
}
