package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.entity.CreditApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditApplicationRepository extends JpaRepository<CreditApplication, Long> {
    List<CreditApplication> findByCreditNoteId(Long creditNoteId);
}
