package com.red_escuela.backend.billing.run.repository;

import com.red_escuela.backend.billing.invoice.domain.InvoiceLine;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.YearMonth;
import java.util.List;

public interface DraftInvoiceLineRepository extends Repository<InvoiceLine, Long> {

    @Query("""
            select l from InvoiceLine l join fetch l.invoice i
            where i.monthlyInvoiceRunId = :runId and i.documentStatus = com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus.DRAFT
            and (l.programEnrollmentId is not null or l.courseEnrollmentId is not null)
            """)
    List<InvoiceLine> findEnrollmentLinesOfDraftInvoicesOfRun(@Param("runId") Long runId);

    @Query("""
            select l from InvoiceLine l join fetch l.invoice i
            where l.period = :period and i.documentStatus = com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus.ISSUED
            and (l.programEnrollmentId is not null or l.courseEnrollmentId is not null)
            """)
    List<InvoiceLine> findEnrollmentLinesOfIssuedInvoicesOfPeriod(@Param("period") YearMonth period);

    @Query("""
            select count(i) from Invoice i
            where i.monthlyInvoiceRunId = :runId and i.documentStatus = com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus.DRAFT
            """)
    long countDraftInvoicesOfRun(@Param("runId") Long runId);
}
