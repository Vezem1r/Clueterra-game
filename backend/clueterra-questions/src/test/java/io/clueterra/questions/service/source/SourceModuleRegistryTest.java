package io.clueterra.questions.service.source;

import io.clueterra.questions.clients.common.DataSourceClient;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.service.hint.lens.CandidateHint;
import io.clueterra.questions.service.hint.lens.HintLens;
import io.clueterra.questions.service.hint.lens.LensRegistry;
import io.clueterra.questions.service.hint.lens.LensType;
import io.clueterra.questions.service.hint.lens.StatisticContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceModuleRegistryTest {

    @Test
    void buildsPoliciesForStaticAndDynamicSources() {
        DataSourceClient worldBankClient = mock(DataSourceClient.class);
        when(worldBankClient.getSource()).thenReturn(DataSource.WORLD_BANK);
        when(worldBankClient.getPriority()).thenReturn(DataSource.WORLD_BANK.getPriority());

        DataSourceClient brandClient = mock(DataSourceClient.class);
        when(brandClient.getSource()).thenReturn(DataSource.GLOBAL_BRANDS);
        when(brandClient.getPriority()).thenReturn(DataSource.GLOBAL_BRANDS.getPriority());
        when(brandClient.isDynamic()).thenReturn(true);

        LensRegistry lensRegistry = mock(LensRegistry.class);
        when(lensRegistry.getAllLenses()).thenReturn(List.of(
                lens("world-bank", DataSource.WORLD_BANK),
                lens("brands", DataSource.GLOBAL_BRANDS)
        ));

        SourceStatisticProcessor processor = mock(SourceStatisticProcessor.class);
        SourceModuleRegistry registry = new SourceModuleRegistry(
                List.of(brandClient, worldBankClient),
                lensRegistry,
                processor
        );

        SourceModule worldBank = registry.require(DataSource.WORLD_BANK);
        SourceModule brands = registry.require(DataSource.GLOBAL_BRANDS);

        assertThat(worldBank.policy().dynamic()).isFalse();
        assertThat(worldBank.policy().comparativeEnrichmentEnabled()).isTrue();
        assertThat(worldBank.lenses()).isNotEmpty();

        assertThat(brands.policy().dynamic()).isTrue();
        assertThat(brands.policy().comparativeEnrichmentEnabled()).isFalse();
        assertThat(brands.lenses()).isNotEmpty();
    }

    @Test
    void modulesAreSortedByClientPriorityAndSourcesWithoutLensRemainValid() {
        DataSourceClient nagerDateClient = mock(DataSourceClient.class);
        when(nagerDateClient.getSource()).thenReturn(DataSource.NAGER_DATE);
        when(nagerDateClient.getPriority()).thenReturn(DataSource.NAGER_DATE.getPriority());

        DataSourceClient worldBankClient = mock(DataSourceClient.class);
        when(worldBankClient.getSource()).thenReturn(DataSource.WORLD_BANK);
        when(worldBankClient.getPriority()).thenReturn(DataSource.WORLD_BANK.getPriority());

        LensRegistry lensRegistry = mock(LensRegistry.class);
        when(lensRegistry.getAllLenses()).thenReturn(List.of(lens("world-bank", DataSource.WORLD_BANK)));

        SourceModuleRegistry registry = new SourceModuleRegistry(
                List.of(nagerDateClient, worldBankClient),
                lensRegistry,
                mock(SourceStatisticProcessor.class)
        );

        assertThat(registry.allModules())
                .extracting(SourceModule::source)
                .containsExactly(DataSource.WORLD_BANK, DataSource.NAGER_DATE);
        assertThat(registry.require(DataSource.NAGER_DATE).lenses()).isEmpty();
    }

    @Test
    void requireThrowsForUnknownSource() {
        SourceModuleRegistry registry = new SourceModuleRegistry(
                List.of(),
                mock(LensRegistry.class),
                mock(SourceStatisticProcessor.class)
        );

        assertThatThrownBy(() -> registry.require(DataSource.WORLD_BANK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown source module");
    }

    @Test
    void groupsEveryLensOwnedByTheSameSourceForCacheInvalidation() {
        DataSourceClient wikidataClient = mock(DataSourceClient.class);
        when(wikidataClient.getSource()).thenReturn(DataSource.WIKIDATA);
        when(wikidataClient.getPriority()).thenReturn(DataSource.WIKIDATA.getPriority());
        HintLens coreLens = mock(HintLens.class);
        HintLens cinemaLens = mock(HintLens.class);
        when(coreLens.claimedSources()).thenReturn(Set.of(DataSource.WIKIDATA));
        when(cinemaLens.claimedSources()).thenReturn(Set.of(DataSource.WIKIDATA));
        LensRegistry lensRegistry = mock(LensRegistry.class);
        when(lensRegistry.getAllLenses()).thenReturn(List.of(coreLens, cinemaLens));
        SourceModuleRegistry registry = new SourceModuleRegistry(
                List.of(wikidataClient),
                lensRegistry,
                mock(SourceStatisticProcessor.class));

        registry.require(DataSource.WIKIDATA).invalidateLensCaches();

        verify(coreLens).invalidateCache();
        verify(cinemaLens).invalidateCache();
    }

    private HintLens lens(String name, DataSource source) {
        return new HintLens() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean canApply(StatisticContext context) {
                return true;
            }

            @Override
            public List<CandidateHint> generate(StatisticContext context) {
                return List.of();
            }

            @Override
            public LensType lensType() {
                return LensType.SPECIALIZED_STATIC;
            }

            @Override
            public Set<DataSource> claimedSources() {
                return Set.of(source);
            }
        };
    }
}
