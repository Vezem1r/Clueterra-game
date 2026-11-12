package io.clueterra.api.budget.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class BudgetRevealPayloadSanitizer {

    private static final List<String> CONVERSION_FIELDS = List.of(
            "sourceAmountLow",
            "sourceAmountHigh",
            "sourceCurrency",
            "amountUsdLow",
            "amountUsdHigh",
            "costYear");
    private static final List<String> FORMULA_FIELDS = List.of(
            "operation",
            "numerator",
            "numeratorUnit",
            "denominator",
            "denominatorUnit",
            "original",
            "final",
            "unit",
            "result",
            "resultUnit");

    public JsonNode sanitize(JsonNode revealPayload) {
        if (!(revealPayload instanceof ObjectNode reveal)) {
            return revealPayload;
        }
        ObjectNode sanitized = reveal.deepCopy();
        sanitizeReveal(sanitized);
        return sanitized;
    }

    private void sanitizeReveal(ObjectNode reveal) {
        reveal.remove("hints");
        removeMilestonePrecision(reveal);
        sanitizeExplanation(reveal);
        sanitizeNestedReveal(reveal, "primary");
        sanitizeNestedReveal(reveal, "comparison");
    }

    private void removeMilestonePrecision(ObjectNode reveal) {
        JsonNode milestones = reveal.get("milestones");
        if (milestones == null || !milestones.isArray()) {
            return;
        }
        milestones.forEach(milestone -> {
            if (milestone instanceof ObjectNode value) {
                value.remove("precision");
            }
        });
    }

    private void sanitizeNestedReveal(ObjectNode reveal, String field) {
        JsonNode nested = reveal.get(field);
        if (nested instanceof ObjectNode nestedReveal) {
            sanitizeReveal(nestedReveal);
        }
    }

    private void sanitizeExplanation(ObjectNode reveal) {
        JsonNode sourceNode = reveal.get("explanation");
        if (!(sourceNode instanceof ObjectNode source)) {
            reveal.remove("explanation");
            return;
        }

        ObjectNode minimal = reveal.objectNode();
        JsonNode conversion = source.get("conversion");
        if (conversion instanceof ObjectNode conversionObject) {
            minimal.set("conversion", copyFields(conversionObject, CONVERSION_FIELDS));
        } else {
            addLegacyConversion(minimal, source.get("answerBasis"));
        }

        JsonNode formula = source.get("formula");
        if (formula instanceof ObjectNode formulaObject) {
            minimal.set("formula", minimalFormula(formulaObject, source));
        }

        if (minimal.isEmpty()) {
            reveal.remove("explanation");
        } else {
            reveal.set("explanation", minimal);
        }
    }

    private void addLegacyConversion(ObjectNode explanation, JsonNode basisNode) {
        if (!(basisNode instanceof ObjectNode basis)) {
            return;
        }
        String currency = basis.path("sourceCurrency").asText("");
        if (currency.isBlank() || "USD".equalsIgnoreCase(currency)) {
            return;
        }
        explanation.set("conversion", copyFields(basis, CONVERSION_FIELDS));
    }

    private ObjectNode minimalFormula(ObjectNode source, ObjectNode legacyExplanation) {
        ObjectNode formula = copyFields(source, FORMULA_FIELDS);
        copyMilestone(source, formula, "planned");
        copyMilestone(source, formula, "actual");
        if (!formula.has("result") && legacyExplanation.has("answerValue")) {
            formula.set("result", legacyExplanation.get("answerValue"));
        }
        if (!formula.has("resultUnit") && legacyExplanation.has("answerUnit")) {
            formula.set("resultUnit", legacyExplanation.get("answerUnit"));
        }
        return formula;
    }

    private void copyMilestone(ObjectNode source, ObjectNode target, String field) {
        JsonNode value = source.get(field);
        if (value instanceof ObjectNode milestone) {
            target.set(field, copyFields(milestone, List.of("date", "year")));
        }
    }

    private ObjectNode copyFields(ObjectNode source, List<String> fields) {
        ObjectNode target = source.objectNode();
        fields.stream()
                .filter(source::hasNonNull)
                .forEach(field -> target.set(field, source.get(field)));
        return target;
    }
}
