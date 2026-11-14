package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.LegalDocumentPublication;
import com.red_escuela.backend.identity.entity.LegalDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface LegalDocumentPublicationRepository extends JpaRepository<LegalDocumentPublication, Long> {

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into legal_document_publication (document_type, version, first_seen_at)
            values (:documentType, :version, :firstSeenAt)
            on conflict (document_type, version) do nothing
            """)
    int insertIfAbsent(@Param("documentType") String documentType, @Param("version") String version,
                       @Param("firstSeenAt") LocalDateTime firstSeenAt);

    Optional<LegalDocumentPublication> findByDocumentTypeAndVersion(LegalDocumentType documentType, String version);
}
