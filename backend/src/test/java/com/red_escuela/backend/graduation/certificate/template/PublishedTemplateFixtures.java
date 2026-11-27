package com.red_escuela.backend.graduation.certificate.template;

import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

public final class PublishedTemplateFixtures {

    private PublishedTemplateFixtures() {
    }

    public static Map<String, Object> currentPublished(JdbcTemplate jdbc, String documentType) {
        return jdbc.queryForMap("select v.id, v.version_number from certificate_template_version v "
                + "join certificate_template t on t.id = v.template_id where t.document_type = ? and v.status = 'PUBLISHED' "
                + "order by v.version_number desc limit 1", documentType);
    }

    public static Long currentPublishedId(JdbcTemplate jdbc, String documentType) {
        return ((Number) currentPublished(jdbc, documentType).get("id")).longValue();
    }

    public static String currentPublishedRef(JdbcTemplate jdbc, String documentType) {
        return "TEMPLATE_V" + currentPublished(jdbc, documentType).get("version_number");
    }

    public static Long publishRebrandedVersion(JdbcTemplate jdbc, String documentType) {
        return jdbc.queryForObject("insert into certificate_template_version (template_id, version_number, status, institute_name, title, "
                + "intro_text, approval_text, dictation_text, qr_label, footer_legend, signatory1_id, signatory2_id, updated_at, published_at, "
                + "published_by_name) select v.template_id, v.version_number + 1, 'PUBLISHED', 'INSTITUTO REPUBLICADO', v.title, v.intro_text, "
                + "v.approval_text, v.dictation_text, v.qr_label, v.footer_legend, v.signatory1_id, v.signatory2_id, now(), now(), 'Test' "
                + "from certificate_template_version v join certificate_template t on t.id = v.template_id "
                + "where t.document_type = ? and v.status = 'PUBLISHED' order by v.version_number desc limit 1 returning id",
                Long.class, documentType);
    }

    public static CertificateSnapshot withBuiltInRef(CertificateSnapshot snapshot, String templateRef) {
        return new CertificateSnapshot(snapshot.holderFullName(), snapshot.documentTypeLabel(), snapshot.documentNumber(),
                snapshot.titleKind(), snapshot.titleName(), snapshot.programLevel(), snapshot.hours(), snapshot.graduationDate(),
                snapshot.issuedOn(), snapshot.institutionName(), snapshot.signatories(), templateRef, snapshot.recipientLabel());
    }
}
