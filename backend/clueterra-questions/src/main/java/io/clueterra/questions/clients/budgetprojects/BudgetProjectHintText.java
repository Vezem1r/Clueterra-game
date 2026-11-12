package io.clueterra.questions.clients.budgetprojects;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class BudgetProjectHintText {

    private BudgetProjectHintText() {
    }

    public static String format(BudgetProjectHintFacts project) {
        return format(project, true);
    }

    public static String format(BudgetProjectHintFacts project, boolean includeName) {
        String type = switch (String.valueOf(project.category())) {
            case "BRIDGE" -> "bridge";
            case "METRO_RAIL" -> "rail or metro project";
            case "STADIUM" -> "stadium";
            case "SKYSCRAPER" -> "skyscraper";
            case "AIRPORT" -> "transport project";
            case "TUNNEL" -> "tunnel";
            case "DAM" -> "dam";
            case "ENERGY" -> "energy project";
            case "SPACE" -> "space project";
            case "ROAD_HIGHWAY" -> "road or highway project";
            case "PORT" -> "port";
            default -> "major construction project";
        };
        String status = switch (String.valueOf(project.lifecycleStatus())) {
            case "COMPLETED" -> project.openingYear() == null
                    ? "that was completed here" : "that opened here in " + project.openingYear();
            case "UNDER_CONSTRUCTION" -> "under construction here";
            case "PLANNED" -> "planned here";
            case "ON_HOLD" -> "whose construction is currently on hold here";
            default -> "located here";
        };
        StringBuilder text = includeName
                ? new StringBuilder(project.name().strip())
                    .append(" is ")
                    .append(type.startsWith("energy") ? "an " : "a ")
                    .append(type)
                    .append(' ')
                    .append(status)
                    .append('.')
                : new StringBuilder("The pictured ")
                    .append(type)
                    .append(' ')
                    .append(unnamedStatus(project.lifecycleStatus(), project.openingYear()))
                    .append('.');

        String metric = formatMetric(project.category(), project.metricType(), project.metricValue());
        if (metric != null) {
            text.append(' ').append(metric);
        }
        if (project.costUsd() != null) {
            text.append(' ').append(costPrefix(project.costKind()))
                    .append(formatUsd(project.costUsd())).append('.');
        }
        return text.toString();
    }

    private static String unnamedStatus(String lifecycleStatus, Integer openingYear) {
        return switch (String.valueOf(lifecycleStatus)) {
            case "COMPLETED" -> openingYear == null
                    ? "was completed here" : "opened here in " + openingYear;
            case "UNDER_CONSTRUCTION" -> "is under construction here";
            case "PLANNED" -> "is planned here";
            case "ON_HOLD" -> "is currently on hold here";
            default -> "is located here";
        };
    }

    private static String costPrefix(String kind) {
        if ("FINAL_COST".equals(kind)) return "Its final cost was approximately ";
        if ("CURRENT_ESTIMATE".equals(kind)) {
            return "Its current cost estimate is approximately ";
        }
        if ("REVISED_BUDGET".equals(kind)) return "Its revised budget is approximately ";
        if ("ORIGINAL_BUDGET".equals(kind)) return "Its original budget was approximately ";
        if ("APPROVED_BUDGET".equals(kind)) return "Its approved budget was approximately ";
        if ("CONTRACT_VALUE".equals(kind)) return "A contract for it was valued at approximately ";
        if ("FINANCING_AMOUNT".equals(kind)) return "Reported financing totals approximately ";
        if ("EXPENDITURE_TO_DATE".equals(kind)) return "Reported spending to date is approximately ";
        return "Its reported cost was approximately ";
    }

    static boolean hasVisibleMetric(BudgetProjectHintFacts project) {
        return formatMetric(project.category(), project.metricType(), project.metricValue()) != null;
    }

    private static String formatMetric(String category, String type, BigDecimal value) {
        if (type == null || value == null) return null;
        if ("AIRPORT".equals(category) && "LENGTH".equals(type)) return null;
        return switch (type) {
            case "LENGTH" -> "SPACE".equals(category)
                    ? "It measures " + distance(value) + " in length."
                    : "It stretches " + distance(value) + ".";
            case "MAIN_SPAN" -> "Its main span reaches " + distance(value) + ".";
            case "HEIGHT" -> "TUNNEL".equals(category) || "SPACE".equals(category)
                    ? "Its reported height is " + distance(value) + "."
                    : "It rises " + distance(value) + ".";
            case "CAPACITY" -> "It can seat about " + whole(value) + " people.";
            case "POWER" -> "Its rated capacity is " + whole(value) + " MW.";
            case "ANNUAL_PASSENGERS" -> "It handles roughly " + whole(value) + " passengers a year.";
            case "CONTAINER_CAPACITY" -> "Its capacity reaches " + whole(value) + " TEU a year.";
            case "ANNUAL_CARGO_TONNES" -> "It handles about " + whole(value) + " tonnes of cargo a year.";
            case "FLOOR_AREA" -> "Its floor area covers about " + whole(value) + " square metres.";
            case "STORAGE_VOLUME" -> "Its storage volume is about " + whole(value) + " cubic metres.";
            case "MASS" -> "Its reported mass is about " + whole(value) + " kilograms.";
            default -> null;
        };
    }

    private static String distance(BigDecimal metres) {
        return metres.compareTo(BigDecimal.valueOf(1_000)) >= 0
                ? metres.divide(BigDecimal.valueOf(1_000), 1, RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString() + " km"
                : metres.setScale(1, RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString() + " metres";
    }

    private static String formatUsd(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.valueOf(1_000_000_000)) >= 0) {
            return "USD " + amount.divide(BigDecimal.valueOf(1_000_000_000), 1, RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString() + " billion";
        }
        if (amount.compareTo(BigDecimal.valueOf(1_000_000)) < 0) {
            return "USD " + whole(amount);
        }
        return "USD " + amount.divide(BigDecimal.valueOf(1_000_000), 1, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + " million";
    }

    private static String whole(BigDecimal value) {
        return String.format(Locale.US, "%,d", value.setScale(0, RoundingMode.HALF_UP).longValue());
    }
}
