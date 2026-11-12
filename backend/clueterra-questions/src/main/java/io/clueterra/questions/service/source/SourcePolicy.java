package io.clueterra.questions.service.source;

public record SourcePolicy(
        boolean dynamic,
        boolean comparativeEnrichmentEnabled,
        boolean enrichmentOnly,
        boolean rerollsArchivedHints
) {
    public static SourcePolicy staticSource(boolean comparativeEnrichmentEnabled) {
        return new SourcePolicy(false, comparativeEnrichmentEnabled, false, false);
    }

    public static SourcePolicy dynamicSource() {
        return new SourcePolicy(true, false, false, false);
    }

    public static SourcePolicy enrichmentOnlySource() {
        return new SourcePolicy(false, false, true, false);
    }

    public SourcePolicy withArchivedHintReroll() {
        return new SourcePolicy(dynamic, comparativeEnrichmentEnabled, enrichmentOnly, true);
    }
}
