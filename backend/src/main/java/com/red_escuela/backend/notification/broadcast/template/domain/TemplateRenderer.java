package com.red_escuela.backend.notification.broadcast.template.domain;

import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.domain.RenderedContent;
import com.red_escuela.backend.notification.broadcast.template.exception.MissingRequiredVariableException;
import com.red_escuela.backend.notification.broadcast.template.exception.UnknownTemplateVariableException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^{}]*)}}");

    private TemplateRenderer() {
    }

    public static void validateVariables(String subject, String bodyText) {
        replacePlaceholders(subject, Map.of(), true);
        replacePlaceholders(bodyText, Map.of(), true);
    }

    public static Set<TemplateVariable> referencedVariables(String subject, String bodyText) {
        Set<TemplateVariable> referenced = EnumSet.noneOf(TemplateVariable.class);
        for (String text : new String[]{subject, bodyText}) {
            if (text != null) {
                Matcher matcher = PLACEHOLDER.matcher(text);
                while (matcher.find()) {
                    String token = matcher.group(1).strip();
                    referenced.add(TemplateVariableCatalog.findByToken(token)
                            .orElseThrow(() -> new UnknownTemplateVariableException(token)));
                }
            }
        }
        return referenced;
    }

    public static RenderedContent render(ChannelType channel, String subject, String bodyText,
                                         Map<TemplateVariable, String> values) {
        String renderedSubject = subject == null ? null : replacePlaceholders(subject, values, false);
        String renderedBody = replacePlaceholders(bodyText, values, false);
        return new RenderedContent(renderedSubject, renderedBody);
    }

    private static String replacePlaceholders(String text, Map<TemplateVariable, String> values,
                                              boolean validateOnly) {
        if (text == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1).strip();
            TemplateVariable variable = TemplateVariableCatalog.findByToken(token)
                    .orElseThrow(() -> new UnknownTemplateVariableException(token));
            String replacement = "";
            if (!validateOnly) {
                String value = values.get(variable);
                if (value == null || value.isBlank()) {
                    throw new MissingRequiredVariableException(token);
                }
                replacement = value;
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
