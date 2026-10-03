package com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import com.red_escuela.backend.evaluation.dto.UpdateSectionEvaluationConfigRequest;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.exception.EvaluationDateOnHolidayException;
import com.red_escuela.backend.evaluation.exception.EvaluationDateOutsideTermException;
import com.red_escuela.backend.evaluation.exception.InvalidSectionEvaluationConfigTransitionException;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.evaluation.service.SectionEvaluationConfigFinder;
import com.red_escuela.backend.evaluation.service.SectionEvaluationConfigInstructorAccess;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@UseCase
public class SaveSectionEvaluationConfigDraftService implements SaveSectionEvaluationConfigDraftUseCase {

    private final SectionEvaluationConfigRepository configRepository;
    private final SectionEvaluationConfigFinder configFinder;
    private final SectionEvaluationConfigInstructorAccess instructorAccess;
    private final HolidayRepository holidayRepository;

    public SaveSectionEvaluationConfigDraftService(SectionEvaluationConfigRepository configRepository,
                                                     SectionEvaluationConfigFinder configFinder,
                                                     SectionEvaluationConfigInstructorAccess instructorAccess,
                                                     HolidayRepository holidayRepository) {
        this.configRepository = configRepository;
        this.configFinder = configFinder;
        this.instructorAccess = instructorAccess;
        this.holidayRepository = holidayRepository;
    }

    @Override
    @Transactional
    public SectionEvaluationConfig save(SaveSectionEvaluationConfigDraftCommand command) {
        instructorAccess.requireLeadInstructor(command.sectionId(), command.instructorId());
        SectionEvaluationConfig config = configFinder.findOrThrow(command.sectionId());
        if (!config.isEditable()) {
            throw new InvalidSectionEvaluationConfigTransitionException(config.getStatus(), "editar");
        }
        UpdateSectionEvaluationConfigRequest request = command.request();
        Term term = config.getSubjectSection().getTerm();
        validateDate(term, request.tp1DueDate());
        validateDate(term, request.tp2DueDate());
        validateDate(term, request.tp3DueDate());
        validateDate(term, request.exam1Date());
        validateDate(term, request.exam2Date());
        validateDate(term, request.exam3Date());
        validateDate(term, request.exam4Date());
        validateDate(term, request.finalCall1Date());
        validateDate(term, request.finalCall2Date());

        config.setTpCount(request.tpCount());
        config.setTp1DueDate(request.tp1DueDate());
        config.setTp2DueDate(request.tp2DueDate());
        config.setTp3DueDate(request.tp3DueDate());
        config.setExam1Date(request.exam1Date());
        config.setExam2Date(request.exam2Date());
        config.setExam3Date(request.exam3Date());
        config.setExam4Date(request.exam4Date());
        config.setFinalCall1Date(request.finalCall1Date());
        config.setFinalCall2Date(request.finalCall2Date());
        return configRepository.save(config);
    }

    private void validateDate(Term term, LocalDate date) {
        if (date == null) {
            return;
        }
        LocalDate start = term.getStartDate() != null ? term.getStartDate().toLocalDate() : null;
        LocalDate end = term.getEndDate() != null ? term.getEndDate().toLocalDate() : null;
        if ((start != null && date.isBefore(start)) || (end != null && date.isAfter(end))) {
            throw new EvaluationDateOutsideTermException(date);
        }
        if (holidayRepository.existsByDate(date)) {
            throw new EvaluationDateOnHolidayException(date);
        }
    }
}
