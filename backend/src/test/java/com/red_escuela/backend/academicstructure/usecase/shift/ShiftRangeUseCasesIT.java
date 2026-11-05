package com.red_escuela.backend.academicstructure.usecase.shift;

import com.red_escuela.backend.academicstructure.domain.SectionShift;
import com.red_escuela.backend.academicstructure.dto.ShiftRangeRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateShiftRangesRequest;
import com.red_escuela.backend.academicstructure.exception.InvalidShiftRangesException;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ShiftRangeUseCasesIT extends AbstractIntegrationTest {

    @Autowired
    private ListShiftRangesUseCase listShiftRanges;
    @Autowired
    private UpdateShiftRangesUseCase updateShiftRanges;

    private static ShiftRangeRequest range(SectionShift shift, LocalTime start, LocalTime end) {
        return new ShiftRangeRequest(shift, start, end);
    }

    @Test
    void defaultRangesAreListedInDayOrder() {
        var ranges = listShiftRanges.list();

        assertThat(ranges).extracting("shift").containsExactly(SectionShift.MORNING, SectionShift.AFTERNOON, SectionShift.EVENING);
        assertThat(ranges.get(0).startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(ranges.get(0).endTime()).isEqualTo(LocalTime.of(12, 30));
        assertThat(ranges.get(2).endTime()).isEqualTo(LocalTime.of(22, 30));
    }

    @Test
    void updatedRangesAreReturnedByTheNextListing() {
        updateShiftRanges.update(new UpdateShiftRangesRequest(List.of(
                range(SectionShift.MORNING, LocalTime.of(7, 30), LocalTime.of(12, 0)),
                range(SectionShift.AFTERNOON, LocalTime.of(13, 0), LocalTime.of(17, 30)),
                range(SectionShift.EVENING, LocalTime.of(18, 0), LocalTime.of(22, 0)))));

        var ranges = listShiftRanges.list();

        assertThat(ranges.get(0).startTime()).isEqualTo(LocalTime.of(7, 30));
        assertThat(ranges.get(1).endTime()).isEqualTo(LocalTime.of(17, 30));
        assertThat(ranges.get(2).startTime()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void overlappingUpdateIsRejectedAndTheStoredRangesStayAsTheyWere() {
        assertThatThrownBy(() -> updateShiftRanges.update(new UpdateShiftRangesRequest(List.of(
                range(SectionShift.MORNING, LocalTime.of(8, 0), LocalTime.of(14, 0)),
                range(SectionShift.AFTERNOON, LocalTime.of(13, 30), LocalTime.of(18, 0)),
                range(SectionShift.EVENING, LocalTime.of(18, 30), LocalTime.of(22, 30))))))
                .isInstanceOf(InvalidShiftRangesException.class);

        assertThat(listShiftRanges.list().get(0).endTime()).isEqualTo(LocalTime.of(12, 30));
    }
}
