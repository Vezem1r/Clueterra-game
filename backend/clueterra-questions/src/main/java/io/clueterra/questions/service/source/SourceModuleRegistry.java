package io.clueterra.questions.service.source;

import io.clueterra.questions.clients.common.DataSourceClient;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.service.hint.lens.HintLens;
import io.clueterra.questions.service.hint.lens.LensRegistry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class SourceModuleRegistry {

    private final Map<DataSource, SourceModule> modulesBySource;
    private final List<SourceModule> modules;

    public SourceModuleRegistry(List<DataSourceClient> clients,
                                LensRegistry lensRegistry,
                                SourceStatisticProcessor statisticProcessor) {
        Map<DataSource, List<HintLens>> lensesBySource = new EnumMap<>(DataSource.class);
        for (HintLens lens : lensRegistry.getAllLenses()) {
            for (DataSource source : lens.claimedSources()) {
                lensesBySource.computeIfAbsent(source, ignored -> new ArrayList<>()).add(lens);
            }
        }

        EnumMap<DataSource, SourceModule> built = new EnumMap<>(DataSource.class);
        clients.stream()
                .sorted(Comparator.comparingInt(DataSourceClient::getPriority))
                .forEach(client -> built.put(
                        client.getSource(),
                        new DefaultSourceModule(
                                client.getSource(),
                                buildPolicy(client),
                                client,
                                statisticProcessor,
                                lensesBySource.getOrDefault(client.getSource(), List.of())
                        )
                ));

        this.modulesBySource = Map.copyOf(built);
        this.modules = List.copyOf(built.values().stream()
                .sorted(Comparator.comparingInt(module -> module.client().getPriority()))
                .toList());
    }

    public List<SourceModule> allModules() {
        return modules;
    }

    public Optional<SourceModule> find(DataSource source) {
        return Optional.ofNullable(modulesBySource.get(source));
    }

    public SourceModule require(DataSource source) {
        return find(source)
                .orElseThrow(() -> new IllegalArgumentException("Unknown source module: " + source));
    }

    private SourcePolicy buildPolicy(DataSourceClient client) {
        if (client.isDynamic()) {
            return SourcePolicy.dynamicSource();
        }
        return switch (client.getSource()) {
            case OPEN_STREET_MAP -> SourcePolicy.enrichmentOnlySource();
            case BUDGET_PROJECTS -> SourcePolicy.staticSource(true).withArchivedHintReroll();
            default -> SourcePolicy.staticSource(true);
        };
    }
}
