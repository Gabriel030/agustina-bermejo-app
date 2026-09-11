package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.PaymentReceipt;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.service.InvoiceFactory;
import com.red_escuela.backend.billing.service.ProgramCohortService;
import com.red_escuela.backend.billing.usecase.paymentreceipt.UploadPaymentReceiptUseCase;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.exception.AlreadyHasPendingApplicationException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentApplicationRepository;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

@UseCase
public class RequestProgramEnrollmentApplicationService implements RequestProgramEnrollmentApplicationUseCase {

    private final ProgramEnrollmentApplicationRepository applicationRepository;
    private final ProgramRepository programRepository;
    private final ProgramEnrollmentService programEnrollmentService;
    private final InvoiceFactory invoiceFactory;
    private final UploadPaymentReceiptUseCase uploadPaymentReceiptUseCase;
    private final ProgramCohortService programCohortService;

    public RequestProgramEnrollmentApplicationService(ProgramEnrollmentApplicationRepository applicationRepository,
                                                        ProgramRepository programRepository,
                                                        ProgramEnrollmentService programEnrollmentService,
                                                        InvoiceFactory invoiceFactory,
                                                        UploadPaymentReceiptUseCase uploadPaymentReceiptUseCase,
                                                        ProgramCohortService programCohortService) {
        this.applicationRepository = applicationRepository;
        this.programRepository = programRepository;
        this.programEnrollmentService = programEnrollmentService;
        this.invoiceFactory = invoiceFactory;
        this.uploadPaymentReceiptUseCase = uploadPaymentReceiptUseCase;
        this.programCohortService = programCohortService;
    }

    @Override
    @Transactional
    public ProgramEnrollmentApplication request(RequestProgramEnrollmentApplicationCommand command) {
        Student student = command.student();
        Program program = programRepository.findById(command.programId())
                .orElseThrow(() -> new ProgramNotFoundException(command.programId()));
        ProgramCohort cohort = programCohortService.findOrThrow(command.cohortId());
        programEnrollmentService.validarVentanaDeAdmision(cohort);

        applicationRepository
                .findByStudentIdAndProgramIdAndStatus(student.getId(), command.programId(), ProgramEnrollmentApplicationStatus.PENDING)
                .ifPresent(existing -> {
                    throw new AlreadyHasPendingApplicationException(student.getId(), command.programId());
                });

        Invoice invoice = invoiceFactory.crearAdHocParaCarrera(program, FeeType.ENROLLMENT_FEE, YearMonth.now(), LocalDate.now());
        PaymentReceipt receipt = uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), command.comprobante());

        ProgramEnrollmentApplication application = new ProgramEnrollmentApplication();
        application.setStudent(student);
        application.setProgram(program);
        application.setProgramCohort(cohort);
        application.setStatus(ProgramEnrollmentApplicationStatus.PENDING);
        application.setRequestedAt(LocalDateTime.now());
        application.setPaymentReceipt(receipt);
        return applicationRepository.save(application);
    }
}
