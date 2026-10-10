package com.red_escuela.backend.config;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RecordRequiredModelConverterTest {

    record Sample(String name, Integer count, Optional<String> note, @Nullable String remark, List<String> tags) {
    }

    @Test
    @SuppressWarnings("rawtypes")
    void recordComponentsAreRequiredUnlessNullable() {
        ModelConverters converters = new ModelConverters();
        converters.addConverter(new RecordRequiredModelConverter());

        Map<String, Schema> schemas = converters.read(Sample.class);
        Schema sample = schemas.get("Sample");

        assertThat(sample.getRequired()).containsExactlyInAnyOrder("name", "count", "tags");
        assertThat(sample.getRequired()).doesNotContain("note", "remark");
    }
}
