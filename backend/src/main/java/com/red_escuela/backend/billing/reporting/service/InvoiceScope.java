package com.red_escuela.backend.billing.reporting.service;

import com.red_escuela.backend.billing.reporting.domain.MetricFilter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.Timestamp;

public final class InvoiceScope {

    private final String conditions;
    private final MapSqlParameterSource parameters;

    private InvoiceScope(String conditions, MapSqlParameterSource parameters) {
        this.conditions = conditions;
        this.parameters = parameters;
    }

    public static InvoiceScope of(MetricFilter filter, String invoiceAlias) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("from", java.sql.Date.valueOf(filter.range().firstDay()))
                .addValue("to", java.sql.Date.valueOf(filter.range().dayAfterLastDay()))
                .addValue("fromTimestamp", Timestamp.valueOf(filter.range().firstDay().atStartOfDay()))
                .addValue("toTimestamp", Timestamp.valueOf(filter.range().dayAfterLastDay().atStartOfDay()))
                .addValue("currency", filter.currency().name());
        StringBuilder conditions = new StringBuilder(" and " + invoiceAlias + ".currency = :currency");
        filter.type().ifPresent(type -> {
            conditions.append(" and ").append(invoiceAlias).append(".invoice_type = :invoiceType");
            parameters.addValue("invoiceType", type.name());
        });
        filter.program().ifPresent(programId -> {
            conditions.append(" and exists (select 1 from invoice_line pl join program_enrollment pe on pe.id = pl.program_enrollment_id ")
                    .append("where pl.invoice_id = ").append(invoiceAlias).append(".id and pe.program_id = :programId)");
            parameters.addValue("programId", programId);
        });
        filter.course().ifPresent(courseId -> {
            conditions.append(" and exists (select 1 from invoice_line cl join course_enrollment ce on ce.id = cl.course_enrollment_id ")
                    .append("where cl.invoice_id = ").append(invoiceAlias).append(".id and ce.course_id = :courseId)");
            parameters.addValue("courseId", courseId);
        });
        return new InvoiceScope(conditions.toString(), parameters);
    }

    public String conditions() {
        return conditions;
    }

    public MapSqlParameterSource parameters() {
        return parameters;
    }
}
