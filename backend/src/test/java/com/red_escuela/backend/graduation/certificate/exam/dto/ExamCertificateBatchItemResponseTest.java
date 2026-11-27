package com.red_escuela.backend.graduation.certificate.exam.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.RecordRequiredModelConverter;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExamCertificateBatchItemResponseTest {

    @Test
    void debtItemSerializesNullStudentNameWithoutFailing() throws Exception {
        ExamCertificateBatchItemResponse item = new ExamCertificateBatchItemResponse(7L, null, ExamCertificateBatchOutcome.DEBT, null);
        ObjectMapper mapper = new ObjectMapper();

        JsonNode json = mapper.readTree(mapper.writeValueAsString(item));

        assertThat(json.has("studentName")).isTrue();
        assertThat(json.get("studentName").isNull()).isTrue();
        assertThat(json.get("outcome").asText()).isEqualTo("DEBT");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void studentNameIsDeclaredNullableAndNotRequiredInTheContract() {
        ModelConverters converters = new ModelConverters();
        converters.addConverter(new RecordRequiredModelConverter());

        Schema schema = converters.read(ExamCertificateBatchItemResponse.class).get("ExamCertificateBatchItemResponse");

        assertThat(schema.getRequired()).containsExactlyInAnyOrder("registrationId", "outcome");
        assertThat(((Schema) schema.getProperties().get("studentName")).getNullable()).isTrue();
    }
}
