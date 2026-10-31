package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLine;
import com.red_escuela.backend.billing.planning.service.MonthlyInstallmentDueDateCalculator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

@Component
public class DraftInvoiceDueDateResolver {

    private final BillingConfigurationReader configurationReader;
    private final JdbcTemplate jdbcTemplate;
    private final MonthlyInstallmentDueDateCalculator dueDateCalculator = new MonthlyInstallmentDueDateCalculator();

    public DraftInvoiceDueDateResolver(BillingConfigurationReader configurationReader, JdbcTemplate jdbcTemplate) {
        this.configurationReader = configurationReader;
        this.jdbcTemplate = jdbcTemplate;
    }

    public LocalDate resolveDueDateOfDraftApprovedOn(Invoice draft, LocalDate approvalDate) {
        if (draft.getFeeType() != FeeType.MONTHLY_INSTALLMENT) {
            throw new IllegalStateException("Only monthly installment drafts can be approved, but draft " + draft.getId() + " is " + draft.getFeeType());
        }
        InvoiceLine line = draft.getPrimaryLine();
        int dueDays = line.getProgramEnrollmentId() != null ? programDueDays(line, approvalDate) : courseDueDays(line, approvalDate);
        return dueDateCalculator.calculateInstallmentDueDate(approvalDate, dueDays);
    }

    private int programDueDays(InvoiceLine line, LocalDate approvalDate) {
        Long templateId = line.getTemplateId();
        if (templateId == null) {
            throw new IllegalStateException("The program installment draft line has no billing template");
        }
        Map<String, Object> template = jdbcTemplate.queryForMap("select program_id, term_id from program_year_billing_template where id = ?", templateId);
        return configurationReader.requireYearTermsEffectiveOn(((Number) template.get("program_id")).longValue(), ((Number) template.get("term_id")).longValue(), approvalDate)
                .recurringFeeDueDays();
    }

    private int courseDueDays(InvoiceLine line, LocalDate approvalDate) {
        Long courseId = jdbcTemplate.queryForObject("select course_id from course_enrollment where id = ?", Long.class, line.getCourseEnrollmentId());
        return configurationReader.courseTermsEffectiveOn(courseId, approvalDate).monthlyInstallmentDueDays();
    }
}
