package com.red_escuela.backend.billing.invoice.service.legacy;

import com.red_escuela.backend.billing.invoice.domain.StudentSnapshot;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingFacts;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.operation.InvoiceIssuingContextLoader;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.billing.exception.PriceRevisionNotFoundException;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.billing.service.PriceRevisionService;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class EnrollmentBackedInvoiceIssuingContextLoader implements InvoiceIssuingContextLoader {

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ProgramRepository programRepository;
    private final CourseRepository courseRepository;
    private final StudentRepository studentRepository;
    private final PriceRevisionService priceRevisionService;
    private final ProgramYearBillingTemplateRepository templateRepository;
    private final InvoiceRepository invoiceRepository;
    private final Clock clock;

    public EnrollmentBackedInvoiceIssuingContextLoader(ProgramEnrollmentRepository programEnrollmentRepository,
                                                       CourseEnrollmentRepository courseEnrollmentRepository, ProgramRepository programRepository,
                                                       CourseRepository courseRepository, StudentRepository studentRepository,
                                                       PriceRevisionService priceRevisionService, ProgramYearBillingTemplateRepository templateRepository,
                                                       InvoiceRepository invoiceRepository, Clock clock) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.programRepository = programRepository;
        this.courseRepository = courseRepository;
        this.studentRepository = studentRepository;
        this.priceRevisionService = priceRevisionService;
        this.templateRepository = templateRepository;
        this.invoiceRepository = invoiceRepository;
        this.clock = clock;
    }

    @Override
    public InvoiceIssuingContext loadContextLockingRecipient(InvoiceIssuingRequest request) {
        lockEnrollmentOf(request);
        Currency offeringCurrency = request.belongsToProgram()
                ? programRepository.findById(request.programId()).orElseThrow().getCurrency()
                : courseRepository.findById(request.courseId()).orElseThrow().getCurrency();
        Optional<PriceRevision> revision = request.isAdHocCharge() ? Optional.empty() : findRevision(request);
        NonNegativeMoney chargeAmount = request.isAdHocCharge()
                ? request.explicitAmount()
                : NonNegativeMoney.of(revision.orElseThrow(() -> new PriceRevisionNotFoundException(
                        "No PriceRevision found for " + request.feeType() + " at " + request.pricingDate())).getMonthlyAmount());
        InvoiceIssuingFacts facts = new InvoiceIssuingFacts(LocalDate.now(clock), offeringCurrency, chargeAmount,
                revision.map(PriceRevision::getId).orElse(null),
                invoiceRepository.findLiveInvoiceIdByChargeKey(request.chargeKey().value()).orElse(null),
                enrollmentFeeCountOf(request), snapshotOf(request.studentId()));
        return new InvoiceIssuingContext(request, facts);
    }

    private void lockEnrollmentOf(InvoiceIssuingRequest request) {
        if (request.programEnrollmentId() != null) {
            programEnrollmentRepository.findByIdForUpdate(request.programEnrollmentId()).orElseThrow();
        }
        if (request.courseEnrollmentId() != null) {
            courseEnrollmentRepository.findByIdForUpdate(request.courseEnrollmentId()).orElseThrow();
        }
    }

    private Optional<PriceRevision> findRevision(InvoiceIssuingRequest request) {
        return request.belongsToProgram()
                ? priceRevisionService.findRevisionForProgram(request.programId(), request.feeType(), request.pricingDate())
                : priceRevisionService.findRevisionForCourse(request.courseId(), request.feeType(), request.pricingDate());
    }

    private Integer enrollmentFeeCountOf(InvoiceIssuingRequest request) {
        return request.templateId() == null ? null : templateRepository.findEnrollmentFeeCountForShare(request.templateId()).orElse(null);
    }

    private StudentSnapshot snapshotOf(Long studentId) {
        Person person = studentRepository.findById(studentId).orElseThrow().getPerson();
        String fullName = Stream.of(person.getName(), person.getLastname()).filter(part -> part != null && !part.isBlank()).collect(Collectors.joining(" "));
        return new StudentSnapshot(fullName, person.getDni(), formatAddress(person.getAddress()));
    }

    private String formatAddress(Address address) {
        if (address == null) {
            return null;
        }
        String formatted = Stream.of(address.getStreet(), address.getNumber(), address.getCity(), address.getState())
                .filter(part -> part != null && !part.isBlank()).collect(Collectors.joining(" "));
        return formatted.isBlank() ? null : formatted;
    }
}
