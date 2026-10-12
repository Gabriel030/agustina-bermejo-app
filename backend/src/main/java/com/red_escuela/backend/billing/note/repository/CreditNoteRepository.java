package com.red_escuela.backend.billing.note.repository;

import com.red_escuela.backend.billing.note.domain.CreditNote;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CreditNoteRepository extends Repository<CreditNote, Long> {

    CreditNote save(CreditNote creditNote);

    Optional<CreditNote> findById(Long id);

    @Query("""
            select distinct n from CreditNote n left join fetch n.lines
            where n.studentCurrentAccountId in (select a.id from StudentCurrentAccount a where a.studentId = :studentId)
            order by n.issueDate desc, n.id desc
            """)
    List<CreditNote> findByStudentIdNewestFirst(@Param("studentId") Long studentId);

    @Query("""
            select distinct n from CreditNote n left join fetch n.lines
            where n.invoiceId = :invoiceId
            order by n.issueDate desc, n.id desc
            """)
    List<CreditNote> findByInvoiceIdNewestFirst(@Param("invoiceId") Long invoiceId);

    boolean existsById(Long id);
}
