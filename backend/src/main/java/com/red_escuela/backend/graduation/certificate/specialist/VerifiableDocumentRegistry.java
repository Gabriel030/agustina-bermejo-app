package com.red_escuela.backend.graduation.certificate.specialist;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class VerifiableDocumentRegistry {

    private static final String INSERT = """
            insert into verifiable_document (code, document_type, source_id, registered_at) values (?, ?, ?, ?)
            """;
    private static final String FIND = "select document_type, source_id from verifiable_document where code = ?";

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public VerifiableDocumentRegistry(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void register(CertificateDocumentType type, Long sourceId, CertificateCode code) {
        jdbcTemplate.update(INSERT, code.value(), type.name(), sourceId, LocalDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public Optional<VerifiableDocumentRef> find(CertificateCode code) {
        List<VerifiableDocumentRef> rows = jdbcTemplate.query(FIND,
                (rs, i) -> new VerifiableDocumentRef(CertificateDocumentType.valueOf(rs.getString(1)), rs.getLong(2)), code.value());
        return rows.stream().findFirst();
    }

    public record VerifiableDocumentRef(CertificateDocumentType type, Long sourceId) {
    }
}
