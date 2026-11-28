package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.HolidayAffectedItem;
import com.red_escuela.backend.academicstructure.api.HolidayImpactPort;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

@Component
public class EvaluationHolidayImpactPortAdapter implements HolidayImpactPort {

    private final ExamBoardRepository examBoardRepository;
    private final SectionEvaluationConfigRepository configRepository;

    public EvaluationHolidayImpactPortAdapter(ExamBoardRepository examBoardRepository,
                                         SectionEvaluationConfigRepository configRepository) {
        this.examBoardRepository = examBoardRepository;
        this.configRepository = configRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HolidayAffectedItem> findAffectedOn(LocalDate date) {
        Stream<HolidayAffectedItem> boards = examBoardRepository
                .findByExamDateGreaterThanEqualAndExamDateLessThan(date.atStartOfDay(), date.plusDays(1).atStartOfDay()).stream()
                .map(board -> new HolidayAffectedItem("EXAM_BOARD", board.getId(), date));
        Stream<HolidayAffectedItem> configs = configRepository.findWithAnyEvaluationDateOn(date).stream()
                .map(config -> new HolidayAffectedItem("EVALUATION_CONFIG_DATE", config.getId(), date));
        return Stream.concat(boards, configs).toList();
    }
}
