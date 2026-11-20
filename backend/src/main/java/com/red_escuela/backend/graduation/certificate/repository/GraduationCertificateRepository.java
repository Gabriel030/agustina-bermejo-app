package com.red_escuela.backend.graduation.certificate.repository;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

import java.util.Optional;

public interface GraduationCertificateRepository extends JpaRepository<GraduationCertificate, Long> {

    Optional<GraduationCertificate> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByProgramEnrollmentIdAndStatus(Long programEnrollmentId, GraduationCertificateStatus status);

    boolean existsByCourseEnrollmentIdAndStatus(Long courseEnrollmentId, GraduationCertificateStatus status);

    @Query(value = """
            select c.* from graduation_certificate c
            where (cast(:kind as text) is null or c.kind = cast(:kind as text))
              and (cast(:status as text) is null or c.status = cast(:status as text))
              and (cast(:issuedFrom as timestamp) is null or c.issued_at >= cast(:issuedFrom as timestamp))
              and (cast(:issuedBefore as timestamp) is null or c.issued_at < cast(:issuedBefore as timestamp))
              and (cast(:pattern as text) is null
                   or c.snapshot ->> 'holderFullName' ilike cast(:pattern as text)
                   or c.snapshot ->> 'documentNumber' ilike cast(:pattern as text)
                   or c.code ilike cast(:pattern as text))
            order by c.issued_at desc, c.id desc
            """,
            countQuery = """
            select count(*) from graduation_certificate c
            where (cast(:kind as text) is null or c.kind = cast(:kind as text))
              and (cast(:status as text) is null or c.status = cast(:status as text))
              and (cast(:issuedFrom as timestamp) is null or c.issued_at >= cast(:issuedFrom as timestamp))
              and (cast(:issuedBefore as timestamp) is null or c.issued_at < cast(:issuedBefore as timestamp))
              and (cast(:pattern as text) is null
                   or c.snapshot ->> 'holderFullName' ilike cast(:pattern as text)
                   or c.snapshot ->> 'documentNumber' ilike cast(:pattern as text)
                   or c.code ilike cast(:pattern as text))
            """,
            nativeQuery = true)
    Page<GraduationCertificate> search(@Param("kind") String kind, @Param("status") String status,
                                       @Param("pattern") String pattern, @Param("issuedFrom") LocalDateTime issuedFrom,
                                       @Param("issuedBefore") LocalDateTime issuedBefore, Pageable pageable);
}
