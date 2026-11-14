package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.LegalAcceptance;
import com.red_escuela.backend.identity.entity.LegalDocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface LegalAcceptanceRepository extends JpaRepository<LegalAcceptance, Long> {

    boolean existsByPersonIdAndDocumentTypeAndVersion(Long personId, LegalDocumentType documentType, String version);

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into legal_acceptance (person_id, document_type, version, accepted_at, ip_hash, user_agent_summary)
            values (:personId, :documentType, :version, :acceptedAt, :ipHash, :userAgentSummary)
            on conflict (person_id, document_type, version) do nothing
            """)
    int insertIfAbsent(@Param("personId") Long personId, @Param("documentType") String documentType,
                       @Param("version") String version, @Param("acceptedAt") LocalDateTime acceptedAt,
                       @Param("ipHash") String ipHash, @Param("userAgentSummary") String userAgentSummary);

    @Query(value = """
            select a.id as id, p.id as personId, p.name as personName, p.lastname as personLastname,
                a.documentType as documentType, a.version as version, a.acceptedAt as acceptedAt
            from LegalAcceptance a, Person p
            where p.id = a.personId
              and (:personId is null or a.personId = :personId)
              and (:documentType is null or a.documentType = :documentType)
              and (:version is null or a.version = :version)
            order by a.acceptedAt desc, a.id desc
            """,
            countQuery = """
            select count(a)
            from LegalAcceptance a
            where (:personId is null or a.personId = :personId)
              and (:documentType is null or a.documentType = :documentType)
              and (:version is null or a.version = :version)
            """)
    Page<LegalAcceptanceRow> search(@Param("personId") Long personId,
                                         @Param("documentType") LegalDocumentType documentType,
                                         @Param("version") String version, Pageable pageable);
}
