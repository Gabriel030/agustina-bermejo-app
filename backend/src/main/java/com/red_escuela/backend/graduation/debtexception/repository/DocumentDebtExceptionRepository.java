package com.red_escuela.backend.graduation.debtexception.repository;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionStatus;
import com.red_escuela.backend.graduation.debtexception.entity.DocumentDebtException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentDebtExceptionRepository
        extends JpaRepository<DocumentDebtException, Long>, JpaSpecificationExecutor<DocumentDebtException> {

    @Query("select e.id from DocumentDebtException e where e.studentId = :studentId and e.documentType = :documentType"
            + " and e.status = com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionStatus.ACTIVE"
            + " and e.expiresAt > :now and (e.registrationId is null or e.registrationId = :registrationId)"
            + " order by e.expiresAt asc, e.id asc")
    List<Long> findActiveIdsFor(@Param("studentId") Long studentId, @Param("documentType") CertificateDocumentType documentType,
                                @Param("registrationId") Long registrationId, @Param("now") LocalDateTime now);

    @Query("select e.id from DocumentDebtException e where e.studentId = :studentId and e.documentType = :documentType"
            + " and e.status = com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionStatus.ACTIVE"
            + " and e.expiresAt > :now and e.registrationId is null order by e.expiresAt asc, e.id asc")
    List<Long> findActiveIdsWithoutRegistration(@Param("studentId") Long studentId,
                                                @Param("documentType") CertificateDocumentType documentType,
                                                @Param("now") LocalDateTime now);

    @Query("select e.id from DocumentDebtException e where e.status = com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionStatus.ACTIVE"
            + " and e.expiresAt <= :now order by e.expiresAt asc")
    List<Long> findDueIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DocumentDebtException e set e.status = com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionStatus.USED,"
            + " e.usedAt = :now, e.usedById = :usedById, e.version = e.version + 1 where e.id = :id"
            + " and e.status = com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionStatus.ACTIVE and e.expiresAt > :now")
    int markUsedIfActive(@Param("id") Long id, @Param("now") LocalDateTime now, @Param("usedById") Long usedById);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DocumentDebtException e set e.status = com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionStatus.EXPIRED,"
            + " e.version = e.version + 1 where e.id = :id"
            + " and e.status = com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionStatus.ACTIVE and e.expiresAt <= :now")
    int markExpiredIfDue(@Param("id") Long id, @Param("now") LocalDateTime now);
}
