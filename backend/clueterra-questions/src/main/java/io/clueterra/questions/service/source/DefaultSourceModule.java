package io.clueterra.questions.service.source;

import io.clueterra.questions.clients.common.DataSourceClient;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.service.hint.lens.HintLens;

import java.util.List;

public record DefaultSourceModule(
        DataSource source,
        SourcePolicy policy,
        DataSourceClient client,
        SourceStatisticProcessor statisticProcessor,
        List<HintLens> lenses
) implements SourceModule {

    public DefaultSourceModule {
        lenses = List.copyOf(lenses);
    }
}
