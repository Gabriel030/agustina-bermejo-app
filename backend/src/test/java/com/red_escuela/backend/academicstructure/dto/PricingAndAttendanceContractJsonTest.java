package com.red_escuela.backend.academicstructure.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.attendance.dto.AttendanceHistoryResponse;
import com.red_escuela.backend.attendance.dto.AttendanceRiskResponse;
import com.red_escuela.backend.attendance.dto.RosterEntryResponse;
import com.red_escuela.backend.enrollment.dto.SubjectAttendanceSummaryResponse;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class PricingAndAttendanceContractJsonTest {

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode json(Object value) throws Exception {
        return objectMapper.readTree(objectMapper.writeValueAsString(value));
    }

    @Test
    void riskResponseExposesPointsUnderPercentNames() throws Exception {
        JsonNode node = json(new AttendanceRiskResponse(
                true, false, Percentage.ofPoints(new BigDecimal("25")), Percentage.ofPoints(new BigDecimal("25"))));

        assertThat(node.get("absencePercent").decimalValue()).isEqualByComparingTo("25");
        assertThat(node.get("maxAbsencePercent").decimalValue()).isEqualByComparingTo("25");
        assertThat(node.has("porcentajeAusentismo")).isFalse();
        assertThat(node.has("maxAbsencePercentage")).isFalse();
        assertThat(node.get("libre").asBoolean()).isFalse();
    }

    @Test
    void rosterHistoryAndSummaryExposeAttendancePercentInPoints() throws Exception {
        Percentage half = Percentage.ofFraction(new BigDecimal("0.5"));

        JsonNode roster = json(new RosterEntryResponse(1L, 2L, "Ana", "30", 1, half, false));
        JsonNode history = json(new AttendanceHistoryResponse.StudentHistoryRow(2L, "Ana", Map.of(), half));
        JsonNode summary = json(new SubjectAttendanceSummaryResponse("Matematica", half));

        assertThat(roster.get("attendancePercent").decimalValue()).isEqualByComparingTo("50");
        assertThat(history.get("attendancePercent").decimalValue()).isEqualByComparingTo("50");
        assertThat(summary.get("attendancePercent").decimalValue()).isEqualByComparingTo("50");
        assertThat(roster.has("attendancePercentage")).isFalse();
    }

    @Test
    void courseAndProgramPricesAreMoneyObjects() throws Exception {
        Course course = Course.builder().name("Curso").term(new Term()).price(new BigDecimal("18000")).currency(Currency.USD).build();
        Program program = Program.builder().name("Carrera").price(new BigDecimal("45000"))
                .enrollmentFeeAmount(new BigDecimal("30000")).currency(Currency.ARS).build();

        JsonNode courseJson = json(CourseResponse.from(course));
        JsonNode programJson = json(ProgramResponse.from(program));

        assertThat(courseJson.get("price")).isEqualTo(json(Money.of(new BigDecimal("18000"), Currency.USD)));
        assertThat(programJson.get("price").get("amount").asText()).isEqualTo("45000.00");
        assertThat(programJson.get("price").get("currency").asText()).isEqualTo("ARS");
        assertThat(programJson.get("enrollmentFeeAmount").get("amount").asText()).isEqualTo("30000.00");
    }

    @Test
    void courseWithoutPriceSerializesNull() throws Exception {
        Course course = Course.builder().name("Curso").term(new Term()).build();

        assertThat(json(CourseResponse.from(course)).get("price").isNull()).isTrue();
    }

    @Test
    void priceRequestsReadMoneyObjects() throws Exception {
        CreateProgramRequest request = objectMapper.readValue(
                "{\"name\":\"X\",\"totalYears\":3,\"price\":{\"amount\":\"45000\",\"currency\":\"USD\"},"
                        + "\"enrollmentFeeAmount\":{\"amount\":\"100\",\"currency\":\"USD\"},\"totalCreditsRequired\":0}",
                CreateProgramRequest.class);

        assertThat(request.price()).isEqualTo(Money.of(new BigDecimal("45000"), Currency.USD));
        assertThat(request.enrollmentFeeAmount()).isEqualTo(Money.of(new BigDecimal("100"), Currency.USD));
    }
}
