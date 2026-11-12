package io.clueterra.questions.service.source;

import io.clueterra.questions.clients.common.DataSourceClient;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.service.hint.lens.HintLens;

import java.util.List;

public interface SourceModule {

    DataSource source();

    SourcePolicy policy();

    DataSourceClient client();

    SourceStatisticProcessor statisticProcessor();

    List<HintLens> lenses();

    default void invalidateLensCaches() {
        lenses().forEach(HintLens::invalidateCache);
    }
}
