package io.clueterra.questions.clients.hydrorivers;

import io.clueterra.questions.storage.ImageStorageService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HydroRiverSkeletonAssetServiceTest {

    private final HydroRiverSkeletonAssetService service =
            new HydroRiverSkeletonAssetService(Optional.<ImageStorageService>empty());

    @Test
    void excludesAuditedCountryWithoutPermanentRivers() {
        assertThat(service.availableVariants("KW")).isEmpty();
    }

    @Test
    void excludesIcelandVariantWithUnrelatedDrauLabel() {
        assertThat(service.availableVariants("IS"))
                .containsExactly(HydroRiverSkeletonVariant.HARD);
    }
}
