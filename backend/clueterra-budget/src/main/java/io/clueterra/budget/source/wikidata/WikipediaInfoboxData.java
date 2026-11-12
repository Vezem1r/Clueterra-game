package io.clueterra.budget.source.wikidata;

import java.util.List;

public record WikipediaInfoboxData(
        List<WikipediaCostMatch> costs,
        List<WikipediaMetricMatch> metrics,
        List<WikipediaMilestoneMatch> milestones) {

    public WikipediaInfoboxData {
        costs = List.copyOf(costs);
        metrics = List.copyOf(metrics);
        milestones = List.copyOf(milestones);
    }
}
