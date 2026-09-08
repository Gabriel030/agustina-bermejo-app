package com.red_escuela.backend.billing.usecase.paymentplan;

import com.red_escuela.backend.billing.dto.CreatePaymentPlanOptionRequest;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.repository.PaymentPlanOptionRepository;
import com.red_escuela.backend.billing.service.PaymentPlanOptionBuilder;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CreatePaymentPlanOptionForProgramService implements CreatePaymentPlanOptionForProgramUseCase {

    private final PaymentPlanOptionRepository paymentPlanOptionRepository;
    private final ProgramRepository programRepository;
    private final PaymentPlanOptionBuilder paymentPlanOptionBuilder;

    public CreatePaymentPlanOptionForProgramService(PaymentPlanOptionRepository paymentPlanOptionRepository, ProgramRepository programRepository,
                                                      PaymentPlanOptionBuilder paymentPlanOptionBuilder) {
        this.paymentPlanOptionRepository = paymentPlanOptionRepository;
        this.programRepository = programRepository;
        this.paymentPlanOptionBuilder = paymentPlanOptionBuilder;
    }

    @Override
    @Transactional
    public PaymentPlanOption create(Long programId, CreatePaymentPlanOptionRequest request) {
        Program program = programRepository.findById(programId).orElseThrow(() -> new ProgramNotFoundException(programId));
        PaymentPlanOption option = paymentPlanOptionBuilder.build(request);
        option.setProgram(program);
        return paymentPlanOptionRepository.save(option);
    }
}
