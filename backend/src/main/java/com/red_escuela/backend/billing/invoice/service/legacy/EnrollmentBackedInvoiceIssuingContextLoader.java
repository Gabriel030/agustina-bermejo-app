package com.red_escuela.backend.billing.invoice.service.legacy;

import com.red_escuela.backend.billing.invoice.domain.StudentSnapshot;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingFacts;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.operation.InvoiceIssuingContextLoader;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.billing.exception.PriceRevisionNotFoundException;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.billing.service.PriceRevisionService;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class EnrollmentBackedInvoiceIssuingContextLoader implements InvoiceIssuingContextLoader {
    private final BillingConfigurationReader configurationReader;
    private final JdbcTemplate jdbcTemplate;
    private final PriceRevisionService priceRevisionService;
    private final ProgramYearBillingTemplateRepository templateRepository;
    private final InvoiceRepository invoiceRepository;
    private final Clock clock;

    public EnrollmentBackedInvoiceIssuingContextLoader(JdbcTemplate jdbcTemplate, PriceRevisionService priceRevisionService,
                                                       ProgramYearBillingTemplateRepository templateRepository, InvoiceRepository invoiceRepository, BillingConfigurationReader configurationReader, Clock clock) {
        this.configurationReader = configurationReader;
        this.jdbcTemplate = jdbcTemplate;
        this.priceRevisionService = priceRevisionService;
        this.templateRepository = templateRepository;
        this.invoiceRepository = invoiceRepository;
        this.clock = clock;
    }

    @Override
    public InvoiceIssuingContext loadContextLockingRecipient(InvoiceIssuingRequest request) {
        boolean enrollmentMatches = lockEnrollmentAndCheckItBelongsToRecipient(request);
        Currency offeringCurrency = Currency.valueOf(request.belongsToProgram()
                ? jdbcTemplate.queryForObject("select currency from program where id = ?", String.class, request.programId())
                : jdbcTemplate.queryForObject("select currency from course where id = ?", String.class, request.courseId()));
        Optional<PriceRevision> revision = request.isAdHocCharge() ? Optional.empty() : findRevision(request);
        NonNegativeMoney chargeAmount = request.isAdHocCharge()
                ? request.explicitAmount()
                : NonNegativeMoney.of(revision.orElseThrow(() -> new PriceRevisionNotFoundException(
                        "No PriceRevision found for " + request.feeType() + " at " + request.pricingDate())).getMonthlyAmount());
        InvoiceIssuingFacts facts = new InvoiceIssuingFacts(LocalDate.now(clock), offeringCurrency, chargeAmount,
                revision.map(PriceRevision::getId).orElse(null), findExistingInvoiceId(request), enrollmentFeeCountOf(request),
                snapshotOf(request.studentId()), enrollmentMatches);
        return new InvoiceIssuingContext(request, facts);
    }

    private Long findExistingInvoiceId(InvoiceIssuingRequest request) {
        return Stream.concat(request.findChargeKey().stream(), request.findEquivalentAdmissionChargeKeys().stream())
                .map(key -> invoiceRepository.findLiveInvoiceIdByChargeKey(key.value()))
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(null);
    }

    private boolean lockEnrollmentAndCheckItBelongsToRecipient(InvoiceIssuingRequest request) {
        if (request.programEnrollmentId() != null) {
            return belongsToRecipient(request, "select student_id, program_id from program_enrollment where id = ? for update", request.programEnrollmentId(), request.programId());
        }
        if (request.courseEnrollmentId() != null) {
            return belongsToRecipient(request, "select student_id, course_id from course_enrollment where id = ? for update", request.courseEnrollmentId(), request.courseId());
        }
        return true;
    }

    private boolean belongsToRecipient(InvoiceIssuingRequest request, String lockQuery, Long enrollmentId, Long offeringId) {
        return jdbcTemplate.query(lockQuery, (row, index) -> request.studentId().equals(row.getLong(1)) && offeringId.equals(row.getLong(2)), enrollmentId)
                .stream().findFirst().orElse(false);
    }

    private Optional<PriceRevision> findRevision(InvoiceIssuingRequest request) {
        return request.belongsToProgram()
                ? priceRevisionService.findRevisionForProgram(request.programId(), request.feeType(), request.pricingDate())
                : priceRevisionService.findRevisionForCourse(request.courseId(), request.feeType(), request.pricingDate());
    }

    private Integer enrollmentFeeCountOf(InvoiceIssuingRequest request) {
        if (request.templateId() == null || templateRepository.lockForShare(request.templateId()).isEmpty()) {
            return null;
        }
        return configurationReader.requireYearTermsEffectiveOn(templateRepository.findProgramIdById(request.templateId()), templateRepository.findTermIdById(request.templateId()),
                request.pricingDate()).enrollmentFeeCount();
    }

    private StudentSnapshot snapshotOf(Long studentId) {
        return jdbcTemplate.queryForObject("""
                select p.name, p.last_name, p.dni, a.street, a.number, a.city, a.state
                from student s join person p on p.id = s.person_id left join address a on a.id = p.address_id
                where s.id = ?
                """, (row, index) -> new StudentSnapshot(joinNonBlank(row.getString("name"), row.getString("last_name")), row.getString("dni"),
                nullIfBlank(joinNonBlank(row.getString("street"), row.getString("number"), row.getString("city"), row.getString("state")))), studentId);
    }

    private static String joinNonBlank(String... parts) {
        return Stream.of(parts).filter(part -> part != null && !part.isBlank()).collect(Collectors.joining(" "));
    }

    private static String nullIfBlank(String value) {
        return value.isBlank() ? null : value;
    }
}
