package com.red_escuela.backend.academicrecord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.red_escuela.backend.academicrecord.dto.StudentGradeItemResponse;
import com.red_escuela.backend.config.GradeWireSchemaCustomizer;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.api.GradeItemView;
import com.red_escuela.backend.evaluation.dto.GradeItemResponse;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.serialization.ValueObjectJacksonModule;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class StudentGradeItemResponseWireTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new ValueObjectJacksonModule());

    private static GradeItem itemScored(String score) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setId(4L);
        GradeItem item = new GradeItem();
        item.setId(9L);
        item.setProgramSubjectSectionEnrollment(enrollment);
        item.setType(GradeItemType.EXAM);
        item.setSequenceNumber(2);
        item.setScore(score == null ? null : Grade.of(new BigDecimal(score)));
        item.setDate(LocalDateTime.of(2026, 5, 4, 10, 30));
        return item;
    }

    private static GradeItemView viewOf(GradeItem item) {
        return new GradeItemView(item.getId(), 4L, item.getType().name(), item.getSequenceNumber(),
                item.getScore(), item.getDate(), null);
    }

    @Test
    void scoredItemSerializesIdenticallyToTheEvaluationResponse() throws Exception {
        GradeItem item = itemScored("7.5");

        assertThat(MAPPER.readTree(MAPPER.writeValueAsString(StudentGradeItemResponse.from(viewOf(item)))))
                .isEqualTo(MAPPER.readTree(MAPPER.writeValueAsString(GradeItemResponse.from(item))));
    }

    @Test
    void unscoredItemSerializesIdenticallyToTheEvaluationResponse() throws Exception {
        GradeItem item = itemScored(null);

        assertThat(MAPPER.readTree(MAPPER.writeValueAsString(StudentGradeItemResponse.from(viewOf(item)))))
                .isEqualTo(MAPPER.readTree(MAPPER.writeValueAsString(GradeItemResponse.from(item))));
    }

    @Test
    void gradeIsWrittenAsABareJsonNumberWithTwoDecimals() throws Exception {
        String json = MAPPER.writeValueAsString(StudentGradeItemResponse.from(viewOf(itemScored("7.5"))));

        assertThat(json).contains("\"score\":7.50");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void customizerReplacesGradeReferencesWithPlainNumberAndDropsTheGradeSchema() {
        Schema response = new ObjectSchema();
        response.addProperty("score", new Schema().$ref("#/components/schemas/Grade"));
        response.addProperty("id", new NumberSchema());
        OpenAPI openApi = new OpenAPI().components(new Components()
                .addSchemas("GradeItemResponse", response)
                .addSchemas("Grade", new ObjectSchema()));

        new GradeWireSchemaCustomizer().customise(openApi);

        assertThat(openApi.getComponents().getSchemas()).doesNotContainKey("Grade");
        assertThat(((Schema) response.getProperties().get("score")).getType()).isEqualTo("number");
        assertThat(((Schema) response.getProperties().get("score")).get$ref()).isNull();
    }
}
