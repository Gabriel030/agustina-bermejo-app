package com.red_escuela.backend.notification.broadcast.template;

import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.domain.RenderedContent;
import com.red_escuela.backend.notification.broadcast.template.domain.TemplateRenderer;
import com.red_escuela.backend.notification.broadcast.template.domain.TemplateVariable;
import com.red_escuela.backend.notification.broadcast.template.exception.MissingRequiredVariableException;
import com.red_escuela.backend.notification.broadcast.template.exception.UnknownTemplateVariableException;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateRendererTest {

    private static Map<TemplateVariable, String> values() {
        Map<TemplateVariable, String> values = new EnumMap<>(TemplateVariable.class);
        values.put(TemplateVariable.FIRST_NAME, "Ana");
        values.put(TemplateVariable.LAST_NAME, "Pérez");
        values.put(TemplateVariable.INSTITUTION, "Instituto Sur");
        values.put(TemplateVariable.PROGRAM, "Tecnicatura");
        values.put(TemplateVariable.SUBJECT, "Álgebra");
        values.put(TemplateVariable.SECTION, "A");
        values.put(TemplateVariable.UNSUBSCRIBE_LINK, "https://x/baja");
        return values;
    }

    @Test
    void replacesAllowedVariables() {
        RenderedContent content = TemplateRenderer.render(ChannelType.WHATSAPP_LINK, "Hola {{nombre}}",
                "{{nombre}} {{apellido}} - {{carrera}} - {{materia}} - {{comision}} - {{institucion}} - {{ linkBaja }}", values());

        assertThat(content.subject()).isEqualTo("Hola Ana");
        assertThat(content.body()).isEqualTo("Ana Pérez - Tecnicatura - Álgebra - A - Instituto Sur - https://x/baja");
    }

    @Test
    void rejectsUnknownVariable() {
        assertThatThrownBy(() -> TemplateRenderer.render(ChannelType.EMAIL, "s", "Hola {{telefono}}", values()))
                .isInstanceOf(UnknownTemplateVariableException.class)
                .satisfies(error -> assertThat(((UnknownTemplateVariableException) error).details()).containsEntry("variable", "telefono"));
    }

    @Test
    void escapesHtmlInHtmlBody() {
        Map<TemplateVariable, String> values = values();
        values.put(TemplateVariable.FIRST_NAME, "<script>alert(1)</script>");

        RenderedContent email = TemplateRenderer.render(ChannelType.EMAIL, "s", "<p>{{nombre}}</p>", values);
        RenderedContent whatsapp = TemplateRenderer.render(ChannelType.WHATSAPP_LINK, "s", "{{nombre}}", values);

        assertThat(email.body()).isEqualTo("<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>");
        assertThat(whatsapp.body()).isEqualTo("<script>alert(1)</script>");
    }

    @Test
    void failsWhenRequiredVariableIsEmpty() {
        Map<TemplateVariable, String> values = values();
        values.put(TemplateVariable.SUBJECT, " ");

        assertThatThrownBy(() -> TemplateRenderer.render(ChannelType.EMAIL, "s", "Materia {{materia}}", values))
                .isInstanceOf(MissingRequiredVariableException.class)
                .satisfies(error -> assertThat(((MissingRequiredVariableException) error).details()).containsEntry("variable", "materia"));
        assertThatThrownBy(() -> TemplateRenderer.render(ChannelType.EMAIL, "s", "Hola {{apellido}}", new EnumMap<>(TemplateVariable.class)))
                .isInstanceOf(MissingRequiredVariableException.class);
    }

    @Test
    void doesNotEvaluateExpressions() {
        assertThatThrownBy(() -> TemplateRenderer.render(ChannelType.EMAIL, "s", "{{7*7}}", values()))
                .isInstanceOf(UnknownTemplateVariableException.class);
        assertThatThrownBy(() -> TemplateRenderer.render(ChannelType.EMAIL, "s", "{{nombre.length()}}", values()))
                .isInstanceOf(UnknownTemplateVariableException.class);

        Map<TemplateVariable, String> values = values();
        values.put(TemplateVariable.FIRST_NAME, "{{apellido}}");
        RenderedContent content = TemplateRenderer.render(ChannelType.WHATSAPP_LINK, "s", "${1+1} #{2} {{nombre}}", values);

        assertThat(content.body()).isEqualTo("${1+1} #{2} {{apellido}}");
    }
}
