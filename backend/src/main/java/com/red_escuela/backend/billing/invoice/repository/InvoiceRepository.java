package com.red_escuela.backend.billing.invoice.repository;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InvoiceRepository extends Repository<Invoice, Long> {

    Invoice save(Invoice invoice);

    Optional<Invoice> findById(Long id);

    @Query("select distinct i from Invoice i left join fetch i.lines where i.id = :id")
    Optional<Invoice> findInvoiceWithLinesById(@Param("id") Long id);
}
