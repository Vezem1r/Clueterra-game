package io.clueterra.questions.service.hint;

import org.apache.commons.text.StringSubstitutor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Component
public class IndicatorTemplateRenderer {

    public String renderTemplate(String template, Map<String, ?> placeholders) {
        return render(template, placeholders);
    }

    public static String render(String template, Map<String, ?> placeholders) {
        if (template == null) {
            return "";
        }
        if (placeholders == null || placeholders.isEmpty()) {
            return template;
        }

        Map<String, String> normalized = placeholders.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue() != null ? String.valueOf(entry.getValue()) : ""
                ));

        StringSubstitutor substitutor = new StringSubstitutor(normalized, "{", "}");
        substitutor.setEnableUndefinedVariableException(false);
        return substitutor.replace(template);
    }
}
