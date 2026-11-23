package com.red_escuela.backend.graduation.repository;

import com.red_escuela.backend.graduation.entity.CertificateReviewStatus;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GraduationRequestRepository extends JpaRepository<GraduationRequest, Long> {

    List<GraduationRequest> findByProgramEnrollmentId(Long programEnrollmentId);

    List<GraduationRequest> findByStatus(GraduationRequestStatus status);

    List<GraduationRequest> findByProgramEnrollmentIdAndStatusNot(Long programEnrollmentId, GraduationRequestStatus status);

    Optional<GraduationRequest> findFirstByProgramEnrollmentIdOrderByIdDesc(Long programEnrollmentId);

    boolean existsByProgramEnrollmentIdAndStatus(Long programEnrollmentId, GraduationRequestStatus status);

    boolean existsByProgramEnrollmentIdAndStatusNot(Long programEnrollmentId, GraduationRequestStatus status);

    Optional<GraduationRequest> findFirstByProgramEnrollmentIdAndStatusOrderByIdDesc(Long programEnrollmentId,
            GraduationRequestStatus status);

    @Query(value = """
            select gr from GraduationRequest gr
            join fetch gr.programEnrollment e
            join fetch e.student s
            join fetch s.person p
            join fetch e.program pr
            left join fetch gr.certificateReviewedBy rb
            where gr.status = com.red_escuela.backend.graduation.entity.GraduationRequestStatus.APPROVED
              and gr.certificateReviewStatus <> com.red_escuela.backend.graduation.entity.CertificateReviewStatus.NOT_REQUIRED
              and (:reviewStatus is null or gr.certificateReviewStatus = :reviewStatus)
              and (:pattern is null or lower(p.lastname) like :pattern or lower(p.name) like :pattern
                   or lower(concat(p.lastname, ' ', p.name)) like :pattern or lower(concat(p.name, ' ', p.lastname)) like :pattern
                   or p.dni like :pattern)
            order by p.lastname, p.name, gr.id
            """,
            countQuery = """
            select count(gr) from GraduationRequest gr
            join gr.programEnrollment e join e.student s join s.person p
            where gr.status = com.red_escuela.backend.graduation.entity.GraduationRequestStatus.APPROVED
              and gr.certificateReviewStatus <> com.red_escuela.backend.graduation.entity.CertificateReviewStatus.NOT_REQUIRED
              and (:reviewStatus is null or gr.certificateReviewStatus = :reviewStatus)
              and (:pattern is null or lower(p.lastname) like :pattern or lower(p.name) like :pattern
                   or lower(concat(p.lastname, ' ', p.name)) like :pattern or lower(concat(p.name, ' ', p.lastname)) like :pattern
                   or p.dni like :pattern)
            """)
    Page<GraduationRequest> findCertificateReviews(@Param("reviewStatus") CertificateReviewStatus reviewStatus,
            @Param("pattern") String pattern, Pageable pageable);

    @Query("""
            select gr from GraduationRequest gr
            join fetch gr.programEnrollment e
            join fetch e.student s
            join fetch s.person p
            join fetch e.program pr
            left join fetch gr.certificateReviewedBy rb
            where gr.id = :id
            """)
    Optional<GraduationRequest> findWithReviewDetailsById(@Param("id") Long id);
}
